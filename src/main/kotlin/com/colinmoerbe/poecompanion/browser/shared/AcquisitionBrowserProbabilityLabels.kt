package com.colinmoerbe.poecompanion.browser.shared

import java.util.Locale

/** Player-facing display of calculated cumulative probability; the Acquisition model owns the math. */
internal object AcquisitionBrowserProbabilityLabels {
    /** Never turn a small positive result into 0.0%, or a sub-certain result into 100.0%. */
    fun cumulativePercent(fraction: Double): String {
        require(fraction in 0.0..1.0) { "Cumulative probability must be between zero and one" }
        val percent = fraction * PERCENT_MULTIPLIER
        return when {
            percent > 0.0 && percent < MIN_ROUNDED_POSITIVE_PERCENT -> "<0.1%"
            percent < PERCENT_MULTIPLIER && percent >= MAX_ROUNDED_SUBCERTAIN_PERCENT -> ">99.9%"
            else -> String.format(Locale.ROOT, "%.1f%%", percent)
        }
    }

    private const val MIN_ROUNDED_POSITIVE_PERCENT = 0.05
    private const val MAX_ROUNDED_SUBCERTAIN_PERCENT = 99.95
    private const val PERCENT_MULTIPLIER = 100.0
}
