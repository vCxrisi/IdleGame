package de.vcxrisi.sternengarten.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import de.vcxrisi.sternengarten.game.engine.Balance
import de.vcxrisi.sternengarten.game.engine.BoardAnalysis
import de.vcxrisi.sternengarten.game.engine.BoardAnalyzer
import de.vcxrisi.sternengarten.game.engine.GameEngine
import de.vcxrisi.sternengarten.game.engine.GameEvent
import de.vcxrisi.sternengarten.game.engine.OfflineReport
import de.vcxrisi.sternengarten.game.model.CrystalOffer
import de.vcxrisi.sternengarten.game.model.GalaxyLaw
import de.vcxrisi.sternengarten.game.model.GameState
import de.vcxrisi.sternengarten.game.model.Hex
import de.vcxrisi.sternengarten.game.model.NebulaTheme
import de.vcxrisi.sternengarten.game.model.SparkStyle
import de.vcxrisi.sternengarten.game.model.StarType
import de.vcxrisi.sternengarten.game.model.StoreProduct
import de.vcxrisi.sternengarten.game.model.Upgrade
import de.vcxrisi.sternengarten.game.save.SaveRepository
import de.vcxrisi.sternengarten.game.save.nowEpochMillis
import de.vcxrisi.sternengarten.store.StoreGateway
import de.vcxrisi.sternengarten.store.StoreListener
import de.vcxrisi.sternengarten.store.StoreOffer
import de.vcxrisi.sternengarten.ui.theme.Palette
import de.vcxrisi.sternengarten.ui.theme.formatNumber

data class Toast(val id: Long, val title: String, val detail: String, val color: Color, val born: Float)

enum class Sheet { NONE, RESEARCH, GOALS, SHOP, BIG_BANG }

enum class GoalsTab(val title: String) { MISSIONS("Missionen"), GALAXY("Galaxie"), ACHIEVEMENTS("Erfolge"), CONSTELLATIONS("Sternbilder") }

enum class ShopTab(val title: String) { CRYSTALS("Kristalle"), ARTIFACTS("Artefakte"), COSMETICS("Kosmetik") }

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
    var state by mutableStateOf(repository.load() ?: GameState(lastSavedEpochMs = nowEpochMillis()))
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

    init {
        catchUp(nowEpochMillis(), showReport = true)
        refreshDay(force = true)
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
            changed = true
        }
        if (changed) {
            analysis = BoardAnalyzer.analyze(state)
            if (selectedHex?.let { it !in state.stars } == true) selectedHex = null
            refreshDay(force = false)
        }

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
        if (seconds >= MIN_OFFLINE_SECONDS) {
            val (next, report) = engine.applyOffline(state, seconds)
            state = next
            if (showReport && report.stardust > 0.0) offlineReport = report
        }
        state = state.copy(comet = null)
        analysis = BoardAnalyzer.analyze(state)
        accumulator = 0.0
        save()
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
            star == null && hex.length() <= Balance.gardenRadius(state) -> plant(hex)
            star == null -> selectedHex = null
            star.type == StarType.BLACK_HOLE && star.stored > 0.0 && selectedHex == hex -> releaseBlackHole(hex)
            else -> selectedHex = if (selectedHex == hex) null else hex
        }
    }

    private fun plant(hex: Hex) {
        selectedHex = null
        val type = selectedType
        if (type !in state.unlocked) return
        val next = engine.plant(state, hex, type)
        if (next == null) {
            toast("Zu wenig Sternenstaub", "${type.displayName} kostet ${formatNumber(Balance.starCost(state, type))}", Palette.Danger)
            return
        }
        update(next)
        dispatch(GameEvent.Planted(hex, type))
    }

    fun levelUp(hex: Hex) {
        val next = engine.levelUp(state, hex) ?: return
        update(next)
        dispatch(GameEvent.LeveledUp(hex, next.stars.getValue(hex).level))
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
        val next = engine.buyUpgrade(state, upgrade) ?: return
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
        sheet = Sheet.NONE
        update(next)
        save()
    }

    fun chooseGalaxy(law: GalaxyLaw) {
        val next = engine.chooseGalaxy(state, law) ?: return
        selectedType = StarType.RED_DWARF
        update(next)
        toast("Willkommen in ${next.galaxyName}", law.displayName, Palette.DarkMatter)
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
        val detail = if (warp > 0) "${reward.label}: +${formatNumber(warp)} Sternenstaub" else reward.label
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
        if (offer == CrystalOffer.BOOST) toast("Kometenrausch!", "×5 Produktion für 5 Minuten", Palette.Boost)
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
        val next = engine.shop.grantPurchase(state, product, transactionId, restored)
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
        state = engine.progression.checkAchievements(next, events)
        analysis = BoardAnalyzer.analyze(state)
        events.forEach(::dispatch)
    }

    private fun dispatch(event: GameEvent) {
        when (event) {
            is GameEvent.Supernova -> toast("Supernova!", "+${formatNumber(event.elements)} Elemente · Nachbarfelder gedüngt", Palette.Elements)
            is GameEvent.Discovered -> toast("Neues Sternbild: ${event.kind.displayName}", "Dauerhaft +10 % auf alles", Palette.Accent)
            is GameEvent.Unlocked -> toast("Neue Sternart: ${event.type.displayName}", event.type.description, Palette.Stardust)
            is GameEvent.BlackHoleReleased -> toast("Ereignishorizont geöffnet", "+${formatNumber(event.amount)} Sternenstaub", Palette.Boost)
            is GameEvent.CometStardust -> toast(
                if (event.meteor) "Meteor gefangen" else "Kometenregen",
                "+${formatNumber(event.amount)} Sternenstaub",
                Palette.Stardust,
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
            is GameEvent.TimeWarped -> toast("Zeitsprung", "+${formatNumber(event.amount)} Sternenstaub", Palette.Stardust)
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
        const val AUTOSAVE_SECONDS = 5.0
        const val TOAST_SECONDS = 3.5f
        const val TOAST_FADE = 0.4f
        const val REVEAL_SECONDS = 6f
        const val MIN_OFFLINE_SECONDS = 30.0
        const val BACKGROUND_GAP_MS = 30_000L
        const val DAY_MS = 86_400_000L
    }
}
