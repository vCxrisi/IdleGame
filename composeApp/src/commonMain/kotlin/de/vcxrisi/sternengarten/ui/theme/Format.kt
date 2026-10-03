package de.vcxrisi.sternengarten.ui.theme

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

private val SUFFIXES = listOf("", "K", "M", "Mrd", "Bio", "Brd", "Trio")

/** Zahl mit deutschem Dezimalkomma und fester Anzahl Nachkommastellen. */
fun formatDecimal(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = (abs(value) * factor).roundToLong()
    val whole = rounded / factor.toLong()
    val fraction = rounded % factor.toLong()
    val sign = if (value < 0 && rounded != 0L) "-" else ""
    val wholeText = whole.toString().reversed().chunked(3).joinToString(".").reversed()
    return if (decimals == 0) "$sign$wholeText" else "$sign$wholeText,${fraction.toString().padStart(decimals, '0')}"
}

/** Kurzformat für große Zahlen: 950 · 1,25K · 34,5M · 1,20aa … */
fun formatNumber(value: Double): String {
    if (value.isNaN()) return "0"
    if (value.isInfinite()) return "∞"
    val v = abs(value)
    if (v < 1_000) {
        return if (v < 10 && v != floor(v)) formatDecimal(value, 1) else formatDecimal(floor(value), 0)
    }
    val group = floor(log10(v) / 3).toInt()
    val mantissa = value / 10.0.pow(group * 3)
    val decimals = when {
        abs(mantissa) < 10 -> 2
        abs(mantissa) < 100 -> 1
        else -> 0
    }
    return formatDecimal(mantissa, decimals) + suffix(group)
}

private fun suffix(group: Int): String {
    if (group < SUFFIXES.size) return SUFFIXES[group]
    // Danach Buchstabenpaare: aa, ab, … az, ba, …
    val index = group - SUFFIXES.size
    val first = 'a' + (index / 26) % 26
    val second = 'a' + index % 26
    return "$first$second"
}

fun formatDuration(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "$h h ${m.toString().padStart(2, '0')} min"
        m > 0 -> "$m min ${s.toString().padStart(2, '0')} s"
        else -> "$s s"
    }
}

fun formatPercent(fraction: Double): String = "+" + formatDecimal(fraction * 100, 0) + " %"
