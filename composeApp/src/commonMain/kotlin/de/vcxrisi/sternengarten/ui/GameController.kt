package de.vcxrisi.sternengarten.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalysis
import de.vcxrisi.sternengarten.game.engine.BoardAnalyzer
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.BridgeBlock
import de.vcxrisi.sternengarten.game.engine.GameEvent
import de.vcxrisi.sternengarten.game.engine.UnlockStatus
import de.vcxrisi.sternengarten.game.engine.OfflineReport
import de.vcxrisi.sternengarten.game.engine.sanitized
import de.vcxrisi.sternengarten.game.model.CrystalOffer
import de.vcxrisi.sternengarten.game.model.GalaxyKind
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.NebulaTheme
import de.vcxrisi.sternengarten.game.model.SparkStyle
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.game.model.runOf
import de.vcxrisi.sternengarten.game.save.RepairReport
import de.vcxrisi.sternengarten.game.save.SaveMigration
import de.vcxrisi.sternengarten.game.save.SaveRepository
import de.vcxrisi.sternengarten.game.save.nowEpochMillis
import de.vcxrisi.sternengarten.store.StoreGateway
import de.vcxrisi.sternengarten.store.StoreListener
import de.vcxrisi.sternengarten.store.StoreOffer
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.formatDuration
import de.vcxrisi.sternengarten.ui.theme.formatNumber
import de.vcxrisi.sternengarten.ui.theme.galaxyColor

data class Toast(val id: Long, val title: String, val detail: String, val color: Color, val born: Float)

enum class Sheet { NONE, RESEARCH, GOALS, SHOP, BIG_BANG, GALAXIES }

enum class GoalsTab(val title: String) { MISSIONS("Missionen"), GALAXY("Galaxie"), ACHIEVEMENTS("Erfolge"), CONSTELLATIONS("Sternbilder") }

enum class ShopTab(val title: String) { CRYSTALS("Kristalle"), ARTIFACTS("Artefakte"), COSMETICS("Kosmetik") }

enum class GalaxiesTab(val title: String) { OVERVIEW("Übersicht"), BRIDGES("Brücken") }

/** Ein gerade geöffnetes Artefakt – für die Enthüllungs-Animation. */
data class CapsuleReveal(val event: GameEvent.CapsuleOpened, val born: Float)

/**
 * Verbindet Engine, Speicherstand, Store und Oberfläche. Der Zustand liegt in Compose-States,
 * damit die Oberfläche direkt darauf reagiert.
 */
class GameController(
    private val repository: SaveRepository = SaveRepository(),
    private val engine: GameEngine = GameEngine(),
    private val store: StoreGateway,
) : StoreListener {
    /** Hinweis, falls ein entgleister Spielstand beim Laden repariert wurde. */
    var repairNotice by mutableStateOf<RepairReport?>(null)

    /** Der aktuelle Spielstand war unlesbar; geladen wurde ein älterer, der unlesbare ist gesichert. */
    var loadNotice by mutableStateOf(false)
        private set

    var state by mutableStateOf(loadState())
        private set
    var analysis: BoardAnalysis by mutableStateOf(BoardAnalyzer.analyze(state))
        private set
    var selectedType by mutableStateOf(StarType.RED_DWARF)
    var selectedHex by mutableStateOf<Hex?>(null)
    var sheet by mutableStateOf(Sheet.NONE)
    var goalsTab by mutableStateOf(GoalsTab.MISSIONS)
    var shopTab by mutableStateOf(ShopTab.CRYSTALS)
    var offlineReport by mutableStateOf<OfflineReport?>(null)
    var capsuleReveal by mutableStateOf<CapsuleReveal?>(null)
        private set
    val toasts = mutableStateListOf<Toast>()

    // Alles Folgende steht vor `init`, sonst würden die Startwerte das Ergebnis von `catchUp` überschreiben.

    /** Ausgewähltes, noch nicht gekauftes Grenzfeld. */
    var selectedField by mutableStateOf<Hex?>(null)
    var galaxiesTab by mutableStateOf(GalaxiesTab.OVERVIEW)

    /** Felder, die sich in der aktiven Galaxie freikaufen lassen. */
    var frontier by mutableStateOf<Set<Hex>>(emptySet())
        private set

    /** Eigene Produktion je Galaxie pro Sekunde (mit Naturgesetz, ohne Kometenrausch und Brückenstaub). */
    var galaxyRates by mutableStateOf<Map<GalaxyKind, Double>>(emptyMap())
        private set

    /** Brückenstaub je Zielgalaxie pro Sekunde. */
    var bridgeInflow by mutableStateOf<Map<GalaxyKind, Double>>(emptyMap())
        private set

    /** Abholbare Galaxie-Ziele je geparkter Galaxie. */
    var claimableByGalaxy by mutableStateOf<Map<GalaxyKind, Int>>(emptyMap())
        private set

    /** Wanduhr für Countdowns, etwa einmal pro Sekunde aktualisiert. */
    var nowMs by mutableLongStateOf(nowEpochMillis())
        private set

    /** Zählt Galaxiewechsel – die Oberfläche setzt daran Kamera und Partikel zurück. */
    var switchCount by mutableIntStateOf(0)
        private set
    var switchedAt by mutableFloatStateOf(-10f)
        private set
    var previousGalaxy by mutableStateOf<GalaxyKind?>(null)
        private set

    /** Zählt Neuanfänge des Gartens (Urknall, neue Gesetze) – die Kamera passt sich daraufhin neu ein. */
    var layoutVersion by mutableIntStateOf(0)
        private set

    /** Preise und Namen aus dem Store, sobald geladen. */
    val storeOffers = mutableStateMapOf<String, StoreOffer>()
    var purchaseInFlight by mutableStateOf<String?>(null)
        private set
    val storeName: String get() = store.name

    /** Gemeinsame Animationsuhr in Sekunden – treibt alle Effekte an. */
    var clock by mutableFloatStateOf(0f)
        private set

    /** Wird von der Oberfläche gesetzt, um Effekte zu Ereignissen abzuspielen. */
    var onEvent: (GameEvent) -> Unit = {}

    val progression get() = engine.progression
    val shop get() = engine.shop

    private var accumulator = 0.0
    private var sinceSave = 0.0
    private var lastWallClock = nowEpochMillis()
    private var lastDay = -1L
    private var toastId = 0L
    private var bgClock = 0.0
    private var activeIncome = 0.0
    private var frontierSource: Set<Hex>? = null

    val galaxies get() = engine.galaxies

    /** Lädt den Spielstand und bringt ihn auf den aktuellen Balancing-Stand – noch vor der Offline-Simulation. */
    private fun loadState(): GameState {
        val loaded = repository.load()
        loadNotice = loaded.unreadable
        val saved = loaded.state
            ?: return GameState(lastSavedEpochMs = nowEpochMillis(), balanceVersion = SaveMigration.CURRENT_BALANCE_VERSION)
        val (migrated, report) = SaveMigration.migrate(saved)
        repairNotice = report
        return migrated
    }

    fun dismissRepairNotice() {
        repairNotice = null
    }

    fun dismissLoadNotice() {
        loadNotice = false
    }

    init {
        catchUp(nowEpochMillis(), showReport = repairNotice == null && !loadNotice)
        refreshDay(force = true)
        refreshFrontier()
    }

    // ------------------------------------------------------------ Spielschleife

    /**
     * Pro Frame aufgerufen. [dt] treibt nur die Animationen; die Simulation folgt der echten Uhr
     * in festen Schritten von 0,1 s, damit auch kurze Unterbrechungen nicht verloren gehen.
     */
    fun frame(dt: Float) {
        clock += dt
        val now = nowEpochMillis()
        val elapsedMs = (now - lastWallClock).coerceAtLeast(0)
        lastWallClock = now
        if (elapsedMs > BACKGROUND_GAP_MS) {
            // App war im Hintergrund oder das Gerät gesperrt: als Offline-Zeit nachholen.
            applyOffline(elapsedMs / 1000.0)
        } else {
            accumulator += elapsedMs / 1000.0
        }
        var changed = false
        while (accumulator >= STEP) {
            accumulator -= STEP
            val result = engine.tick(state, STEP)
            state = result.state
            result.events.forEach(::dispatch)
            activeIncome += result.ownIncome
            bgClock += STEP
            changed = true
        }
        // Geparkte Galaxien, Brücken und Timer laufen etwa einmal pro Sekunde – nach einer Pause gebündelt.
        while (bgClock >= Balance.BACKGROUND_STEP - 1e-9) runBackground(minOf(bgClock, MAX_BACKGROUND_STEP))
        if (changed) {
            analysis = BoardAnalyzer.analyze(state)
            if (selectedHex?.let { it !in state.stars } == true) selectedHex = null
            refreshFrontier()
            if (selectedField?.let { it !in frontier } == true) selectedField = null
            refreshDay(force = false)
        }
        if (now / 1000 != nowMs / 1000) nowMs = now

        sinceSave += dt
        if (sinceSave >= AUTOSAVE_SECONDS) save()

        toasts.removeAll { clock - it.born > TOAST_SECONDS }
        if (capsuleReveal?.let { clock - it.born > REVEAL_SECONDS } == true) capsuleReveal = null
    }

    private fun catchUp(now: Long, showReport: Boolean) {
        val last = state.lastSavedEpochMs.takeIf { it > 0 } ?: now
        applyOffline((now - last) / 1000.0, showReport)
        lastWallClock = now
    }

    private fun applyOffline(seconds: Double, showReport: Boolean = true) {
        val now = nowEpochMillis()
        var report: OfflineReport? = null
        if (seconds >= MIN_OFFLINE_SECONDS) {
            val (next, offline) = engine.galaxies.applyOfflineAll(state, seconds)
            state = next
            report = offline
        }
        // Timer laufen nach der Wanduhr – auch nach einem kurzen Neustart.
        val (timed, timerEvents) = engine.galaxies.completeTimers(state, now)
        state = engine.withoutEvent(timed).copy(comet = null)
        timerEvents.forEach(::dispatch)
        if (timerEvents.isNotEmpty()) report = (report ?: OfflineReport(seconds, seconds, 0.0, 0.0, 0)).withTimers(timerEvents)
        val worthShowing = report != null && (
            report.stardust > 0.0 || report.galaxies.any { it.stardust > 0.0 || it.bridged > 0.0 } ||
                report.unlocked.isNotEmpty() || report.bridgesCompleted.isNotEmpty()
            )
        if (showReport && worthShowing) offlineReport = report
        analysis = BoardAnalyzer.analyze(state)
        accumulator = 0.0
        bgClock = 0.0
        activeIncome = 0.0
        nowMs = now
        refreshRates()
        refreshFrontier()
        save()
    }

    /** Ein Hintergrund-Schritt über [dt] Sekunden: geparkte Galaxien, Brücken, fertige Timer. */
    private fun runBackground(dt: Double) {
        val result = engine.galaxies.tickBackground(state, dt, activeIncome * dt / bgClock.coerceAtLeast(dt))
        bgClock -= dt
        activeIncome = if (bgClock > 1e-9) activeIncome - activeIncome * dt / (bgClock + dt) else 0.0
        galaxyRates = result.rates
        bridgeInflow = result.inflow
        claimableByGalaxy = result.claimable
        val (timed, timerEvents) = engine.galaxies.completeTimers(result.state, nowEpochMillis())
        state = timed
        result.events.forEach(::dispatch)
        timerEvents.forEach(::dispatch)
    }

    /** Raten aller Galaxien ohne zu ticken – nach Wechsel, Offline-Zeit und Neuanfang. */
    private fun refreshRates() {
        galaxyRates = engine.galaxies.ownRates(state)
        claimableByGalaxy = state.parked.keys.associateWith { engine.galaxies.claimableGoals(state, it) }
        if (state.bridges.none { it.built }) bridgeInflow = emptyMap()
    }

    /** Grenzfelder neu berechnen, aber nur, wenn sich die eigenen Felder geändert haben. */
    private fun refreshFrontier() {
        if (state.ownedFields === frontierSource) return
        frontierSource = state.ownedFields
        frontier = Balance.frontier(state)
    }

    /** Tageswechsel: Login-Belohnung, neue Missionen, fehlende Galaxie-Ziele. */
    private fun refreshDay(force: Boolean) {
        val today = nowEpochMillis() / DAY_MS
        if (!force && today == lastDay) return
        lastDay = today
        update(engine.progression.refreshDaily(state, today))
    }

    fun save() {
        sinceSave = 0.0
        state = state.copy(lastSavedEpochMs = nowEpochMillis())
        repository.save(state)
    }

    // ------------------------------------------------------------ Garten

    fun tapCell(hex: Hex) {
        val star = state.stars[hex]
        when {
            star == null && hex in state.ownedFields -> {
                selectedField = null
                plant(hex)
            }
            star == null && hex in frontier -> {
                selectedHex = null
                selectedField = if (selectedField == hex) null else hex
            }
            star == null -> {
                selectedHex = null
                selectedField = null
            }
            star.type == StarType.BLACK_HOLE && star.stored > 0.0 && selectedHex == hex -> releaseBlackHole(hex)
            else -> {
                selectedField = null
                selectedHex = if (selectedHex == hex) null else hex
            }
        }
    }

    /** Kauft das Grenzfeld [hex] frei. */
    fun buyField(hex: Hex) {
        val result = engine.buyField(state, hex)
        if (result == null) {
            if (hex in frontier && state.lawChoices.isEmpty()) {
                toast("Zu wenig $dustName", "Das Feld kostet ${formatNumber(Balance.fieldCost(state))}", Palette.Danger)
            }
            return
        }
        val (next, cost) = result
        selectedField = null
        update(next)
        dispatch(GameEvent.FieldBought(hex, cost))
    }

    private fun plant(hex: Hex) {
        selectedHex = null
        val type = selectedType
        if (type !in state.unlocked) return
        val next = engine.plant(state, hex, type)
        if (next == null) {
            toast("Zu wenig $dustName", "${type.displayName} kostet ${formatNumber(Balance.starCost(state, type))}", Palette.Danger)
            return
        }
        update(next)
        dispatch(GameEvent.Planted(hex, type))
    }

    fun levelUp(hex: Hex, count: Int = 1) {
        val next = engine.levelUp(state, hex, count) ?: return
        update(next)
        dispatch(GameEvent.LeveledUp(hex, next.stars.getValue(hex).level, count))
    }

    fun canLevel(type: StarType): Boolean = engine.canLevel(type)

    fun levelUpMax(hex: Hex) {
        val (next, count) = engine.levelUpMax(state, hex) ?: return
        update(next)
        dispatch(GameEvent.LeveledUp(hex, next.stars.getValue(hex).level, count))
    }

    fun remove(hex: Hex) {
        val next = engine.remove(state, hex) ?: return
        selectedHex = null
        update(next)
        dispatch(GameEvent.Removed(hex))
    }

    fun isSheltered(hex: Hex): Boolean = engine.isSheltered(state, hex)

    fun releaseBlackHole(hex: Hex) {
        val (next, amount) = engine.releaseBlackHole(state, hex) ?: return
        update(next)
        dispatch(GameEvent.BlackHoleReleased(hex, amount))
    }

    fun buy(upgrade: Upgrade) {
        var next = engine.buyUpgrade(state, upgrade) ?: return
        // Raumfaltung beschleunigt auch Timer, die schon laufen.
        if (upgrade == Upgrade.SPACE_FOLD) next = engine.galaxies.rescaleTimers(next, nowEpochMillis(), Balance.SPACE_FOLD_FACTOR)
        update(next)
    }

    fun catchComet() {
        val (next, event) = engine.catchComet(state) ?: return
        update(next)
        dispatch(event)
    }

    // ------------------------------------------------------------ Urknall

    fun bigBang() {
        val next = engine.bigBang(state) ?: return
        selectedHex = null
        selectedField = null
        sheet = Sheet.NONE
        update(next)
        layoutVersion++
        refreshRates()
        save()
    }

    fun chooseGalaxy(law: GalaxyLaw) {
        val next = engine.chooseGalaxy(state, law) ?: return
        selectedType = StarType.RED_DWARF
        update(next)
        layoutVersion++
        refreshRates()
        toast("Willkommen in ${next.galaxyName}", law.displayName, galaxyColor(next.activeGalaxy))
        save()
    }

    // ------------------------------------------------------------ Galaxien

    /** Name des Staubs der aktiven Galaxie. */
    val dustName: String get() = state.activeGalaxy.dustName

    /** Dunkle Materie, die ein Urknall der Galaxie [kind] jetzt brächte; 0 ohne Galaxie. */
    fun previewGain(kind: GalaxyKind): Double = engine.galaxies.previewGain(state, kind)

    fun unlockStatus(): UnlockStatus = engine.galaxies.unlockStatus(state)

    fun nextUnlockable(): GalaxyKind? = engine.galaxies.nextUnlockable(state)

    fun bridgeBlock(from: GalaxyKind, to: GalaxyKind): BridgeBlock = engine.galaxies.bridgeBlock(state, from, to)

    /** Wechselt in die Galaxie [kind]. Geparkte Galaxien werden vorher auf den aktuellen Stand gebracht. */
    fun switchGalaxy(kind: GalaxyKind) {
        if (kind == state.activeGalaxy) return
        if (bgClock > 0.0) runBackground(bgClock)
        val next = engine.galaxies.switchTo(state, kind) ?: return
        previousGalaxy = state.activeGalaxy
        selectedHex = null
        selectedField = null
        sheet = Sheet.NONE
        if (selectedType.exclusiveTo.let { it != null && it != kind }) selectedType = StarType.RED_DWARF
        switchedAt = clock
        switchCount++
        update(next)
        refreshRates()
        refreshDay(force = true)
        save()
    }

    /** Beginnt die Erschließung der nächsten Galaxie. */
    fun startGalaxyUnlock() {
        val result = engine.galaxies.startUnlock(state, nowEpochMillis())
        if (result == null) {
            val next = nextUnlockable()
            val previous = next?.let { GalaxyKind.entries.getOrNull(it.ordinal - 1) }
            when (unlockStatus()) {
                UnlockStatus.RUNNING -> toast("Nur eine Erschließung gleichzeitig", "Warte, bis die neue Galaxie entstanden ist.", Palette.Danger)
                UnlockStatus.NEEDS_LAW_CHOICE ->
                    toast("Noch nicht möglich", "Wähle zuerst die Naturgesetze der ${previous?.displayName.orEmpty()}.", Palette.Danger)
                UnlockStatus.NEEDS_COLLAPSE ->
                    toast("Noch nicht möglich", "Voraussetzung: ein Urknall in der ${previous?.displayName.orEmpty()}.", Palette.Danger)
                UnlockStatus.NEEDS_DARK_MATTER ->
                    toast("Zu wenig Dunkle Materie", "Benötigt ${formatNumber(next?.unlockDarkMatter ?: 0.0)}", Palette.Danger)
                else -> Unit
            }
            return
        }
        val (next, event) = result
        update(next)
        toast(
            "Neue Galaxie entsteht",
            "${event.kind.displayName} ist in ${formatDuration((event.readyAtMs - nowEpochMillis()) / 1000.0)} bereit",
            galaxyColor(event.kind),
        )
        save()
    }

    /** Stellt die laufende Erschließung für Kristalle sofort fertig. */
    fun skipGalaxyUnlock() {
        val now = nowEpochMillis()
        val (paid, cost) = engine.galaxies.skipUnlock(state, now) ?: return toast("Zu wenig Kristalle", "Für das sofortige Erschließen", Palette.Danger)
        finishSkip(paid, cost, now)
    }

    fun buildBridge(from: GalaxyKind, to: GalaxyKind) {
        val (next, _) = engine.galaxies.buildBridge(state, from, to, nowEpochMillis()) ?: return
        update(next)
        toast("Sternenbrücke im Bau", "${from.displayName} → ${to.displayName}", Palette.Accent)
        save()
    }

    fun skipBridge(from: GalaxyKind, to: GalaxyKind) {
        val now = nowEpochMillis()
        val (paid, cost) = engine.galaxies.skipBridge(state, from, to, now)
            ?: return toast("Zu wenig Kristalle", "Für die sofortige Fertigstellung", Palette.Danger)
        finishSkip(paid, cost, now)
    }

    fun removeBridge(from: GalaxyKind, to: GalaxyKind) {
        update(engine.galaxies.removeBridge(state, from, to))
        refreshRates()
        save()
    }

    private fun finishSkip(paid: GameState, cost: Int, now: Long) {
        val (done, events) = engine.galaxies.completeTimers(paid, now)
        update(done)
        toast("Sofort fertig", "−$cost Kristalle", Palette.Crystal)
        events.forEach(::dispatch)
        refreshRates()
        save()
    }

    // ------------------------------------------------------------ Ziele

    fun claimMission(index: Int) {
        val mission = state.missions.getOrNull(index) ?: return
        val next = engine.progression.claimMission(state, index) ?: return
        update(next)
        toast("Mission erfüllt", "+${mission.crystals} Kristalle", Palette.Crystal)
    }

    fun claimGoal(index: Int) {
        val goal = state.galaxyGoals.getOrNull(index) ?: return
        val (next, allDone) = engine.progression.claimGoal(state, index) ?: return
        update(next)
        toast(
            "Galaxie-Ziel erreicht",
            "+${formatNumber(goal.darkMatter)} Dunkle Materie · +${goal.crystals} Kristalle" + if (allDone) " · Bonus-Kapsel!" else "",
            Palette.DarkMatter,
        )
    }

    fun claimLoginReward() {
        val (next, reward, warp) = engine.progression.claimLoginReward(state) ?: return
        update(next)
        val detail = if (warp > 0) "${reward.label}: +${formatNumber(warp)} $dustName" else reward.label
        toast("Tagesbelohnung · Tag ${state.loginStreak}", detail, Palette.Crystal)
        save()
    }

    // ------------------------------------------------------------ Shop

    fun buyOffer(offer: CrystalOffer) {
        val result = engine.shop.buyOffer(state, offer)
        if (result == null) {
            toast("Zu wenig Kristalle", "${offer.displayName} kostet ${offer.price} Kristalle", Palette.Danger)
            return
        }
        val (next, warp) = result
        update(next)
        if (warp > 0) dispatch(GameEvent.TimeWarped(warp))
        if (offer == CrystalOffer.BOOST) toast("Kometenrausch!", "×5 Produktion in dieser Galaxie für 5 Minuten", Palette.Boost)
        save()
    }

    fun openCapsule() {
        val (next, event) = engine.shop.openCapsule(state) ?: return
        update(next)
        capsuleReveal = CapsuleReveal(event, clock)
        dispatch(event)
        save()
    }

    fun dismissCapsuleReveal() {
        capsuleReveal = null
    }

    fun selectTheme(theme: NebulaTheme) {
        val next = engine.shop.buyTheme(state, theme)
        if (next == null) {
            toast("Zu wenig Kristalle", "${theme.displayName} kostet ${theme.price} Kristalle", Palette.Danger)
            return
        }
        update(next)
    }

    fun selectSpark(spark: SparkStyle) {
        val next = engine.shop.buySpark(state, spark)
        if (next == null) {
            toast("Zu wenig Kristalle", "${spark.displayName} kostet ${spark.price} Kristalle", Palette.Danger)
            return
        }
        update(next)
    }

    // ------------------------------------------------------------ Store

    fun startStore() = store.start(this)

    fun stopStore() = store.stop()

    fun purchase(product: StoreProduct) {
        if (!product.consumable && state.owns(product)) {
            toast("Bereits gekauft", product.displayName, Palette.TextDim)
            return
        }
        purchaseInFlight = product.productId
        store.purchase(product.productId)
    }

    fun restorePurchases() {
        store.restorePurchases()
        toast("Käufe werden wiederhergestellt", store.name, Palette.TextDim)
    }

    override fun onProductsLoaded(offers: List<StoreOffer>) {
        offers.forEach { storeOffers[it.productId] = it }
    }

    override fun onPurchaseSucceeded(productId: String, transactionId: String, restored: Boolean) {
        if (purchaseInFlight == productId) purchaseInFlight = null
        val product = StoreProduct.byId(productId)
        if (product == null) {
            store.finish(transactionId, consumable = true)
            return
        }
        var next = engine.shop.grantPurchase(state, product, transactionId, restored)
        // Der Galaxie-Pionier halbiert auch Timer, die schon laufen.
        if (next != null && product == StoreProduct.GALAXY_PIONEER && !state.owns(product)) {
            next = engine.galaxies.rescaleTimers(next, nowEpochMillis(), Balance.PIONEER_TIMER_MULT)
        }
        if (next != null) {
            update(next)
            // Erst speichern, dann beim Store abschließen – so geht kein Kauf verloren.
            save()
            dispatch(GameEvent.PurchaseGranted(product, restored))
        }
        store.finish(transactionId, product.consumable)
    }

    override fun onPurchasePending(productId: String) {
        if (purchaseInFlight == productId) purchaseInFlight = null
        toast("Kauf ausstehend", "Wird gutgeschrieben, sobald die Zahlung bestätigt ist.", Palette.TextDim)
    }

    override fun onPurchaseFailed(productId: String, message: String) {
        purchaseInFlight = null
        toast("Kauf fehlgeschlagen", message.ifBlank { "Bitte später erneut versuchen." }, Palette.Danger)
    }

    // ------------------------------------------------------------ Intern

    fun dismissOfflineReport() {
        offlineReport = null
    }

    /** Übernimmt einen neuen Zustand und vergibt dabei fällige Erfolge. */
    private fun update(next: GameState) {
        val events = ArrayList<GameEvent>()
        state = engine.progression.checkAchievements(next, events).sanitized()
        analysis = BoardAnalyzer.analyze(state)
        refreshFrontier()
        events.forEach(::dispatch)
    }

    private fun dispatch(event: GameEvent) {
        when (event) {
            is GameEvent.Supernova -> toast("Supernova!", "+${formatNumber(event.elements)} Elemente · Nachbarfelder gedüngt", Palette.Elements)
            is GameEvent.Discovered -> toast("Neues Sternbild: ${event.kind.displayName}", "Dauerhaft +10 % auf alles", Palette.Accent)
            is GameEvent.Unlocked -> toast("Neue Sternart: ${event.type.displayName}", event.type.description, Palette.Stardust)
            is GameEvent.BlackHoleReleased -> toast("Ereignishorizont geöffnet", "+${formatNumber(event.amount)} $dustName", Palette.Boost)
            is GameEvent.CometStardust -> toast(
                if (event.meteor) "Meteor gefangen" else "Kometenregen",
                "+${formatNumber(event.amount)} $dustName",
                galaxyColor(state.activeGalaxy),
            )
            is GameEvent.CometBoost -> toast("Kometenrausch!", "×5 Produktion für ${event.seconds.toInt()} s", Palette.Boost)
            is GameEvent.EventStarted -> toast(event.kind.displayName, event.kind.description, Color.hsv(event.kind.hue, 0.5f, 1f))
            is GameEvent.AchievementUnlocked -> toast(
                "Erfolg: ${event.achievement.title}",
                "+${event.achievement.crystals} Kristalle · dauerhaft +2 % Produktion",
                Palette.Crystal,
            )
            is GameEvent.PurchaseGranted -> toast(
                if (event.restored) "Kauf wiederhergestellt" else "Danke für deinen Kauf!",
                event.product.displayName,
                Palette.Crystal,
            )
            is GameEvent.TimeWarped -> toast("Zeitsprung", "+${formatNumber(event.amount)} $dustName", galaxyColor(state.activeGalaxy))
            is GameEvent.GalaxyUnlocked -> toast(
                "Neue Galaxie entstanden!",
                "${event.kind.displayName} – wähle ihre Naturgesetze",
                galaxyColor(event.kind),
            )
            is GameEvent.BridgeCompleted -> toast(
                "Sternenbrücke steht",
                "${event.bridge.from.displayName} → ${event.bridge.to.displayName}",
                Palette.Accent,
            )
            // Ereignisse geparkter Galaxien und Feldkäufe kommen ohne Toast aus.
            else -> Unit
        }
        onEvent(event)
    }

    private fun toast(title: String, detail: String, color: Color) {
        toasts.add(Toast(toastId++, title, detail, color, clock))
        while (toasts.size > 3) toasts.removeAt(0)
    }

    companion object {
        const val STEP = 0.1

        /** Größter Hintergrund-Schritt, wenn sich nach einer Pause mehrere Sekunden angesammelt haben. */
        const val MAX_BACKGROUND_STEP = 5.0
        const val AUTOSAVE_SECONDS = 5.0
        const val TOAST_SECONDS = 3.5f
        const val TOAST_FADE = 0.4f
        const val REVEAL_SECONDS = 6f
        const val MIN_OFFLINE_SECONDS = 30.0
        const val BACKGROUND_GAP_MS = 30_000L
        const val DAY_MS = 86_400_000L
    }
}
