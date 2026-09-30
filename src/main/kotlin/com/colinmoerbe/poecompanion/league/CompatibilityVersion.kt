package com.colinmoerbe.poecompanion.league

/**
 * Broad PoE compatibility boundary used for knowledge reviewed at league-cycle granularity.
 */
data class CompatibilityVersion(val major: Int, val minor: Int) {
    init {
        require(major >= 0) { "Major version must not be negative" }
        require(minor >= 0) { "Minor version must not be negative" }
    }

    override fun toString(): String = "$major.$minor"
}
