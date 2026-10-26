package id.mirrorselfie

import java.util.Locale

fun shutterLabel(ns: Long): String = when {
    ns == 0L -> "Auto"
    ns >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f s", ns / 1_000_000_000.0)
    else -> "1/${kotlin.math.round(1_000_000_000.0 / ns).toInt()} s"
}
fun recordingLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
