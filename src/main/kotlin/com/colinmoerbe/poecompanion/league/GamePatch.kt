package com.colinmoerbe.poecompanion.league

/**
 * Precise PoE patch identifier used when knowledge depends on an exact effective game version.
 *
 * The optional suffix preserves the official patch suffix without imposing provider-specific parsing rules.
 */
data class GamePatch(
    val compatibilityVersion: CompatibilityVersion,
    val patch: Int,
    val suffix: String? = null,
) {
    init {
        require(patch >= 0) { "Patch version must not be negative" }
        require(suffix == null || suffix.isNotBlank()) { "Patch suffix must not be blank" }
    }

    override fun toString(): String = buildString {
        append(compatibilityVersion)
        append('.')
        append(patch)
        suffix?.let(::append)
    }
}
