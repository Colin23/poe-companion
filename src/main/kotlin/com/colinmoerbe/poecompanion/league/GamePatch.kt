package com.colinmoerbe.poecompanion.league

/**
 * Precise PoE patch identifier used when knowledge depends on an exact effective game version.
 *
 * The optional suffix preserves the official patch suffix without imposing provider-specific parsing rules.
 *
 * Patch ordering is intentionally available only within one compatibility version. Hotfix events are not GamePatch
 * identifiers and are therefore outside this ordering.
 */
data class GamePatch(val compatibilityVersion: CompatibilityVersion, val patch: Int, val suffix: String? = null) {
    init {
        require(patch >= 0) { "Patch version must not be negative" }
        require(suffix == null || suffix.isNotBlank()) { "Patch suffix must not be blank" }
    }

    /**
     * Compares two official patch identifiers inside the same compatibility version.
     *
     * Numeric patch comes first. For the same numeric patch, the unsuffixed release precedes suffixed releases and
     * suffixes are ordered lexically.
     */
    fun compareWithinCompatibilityVersion(other: GamePatch): Int {
        require(compatibilityVersion == other.compatibilityVersion) {
            "Game patches can only be ordered within the same compatibility version"
        }

        val patchComparison = patch.compareTo(other.patch)
        if (patchComparison != 0) {
            return patchComparison
        }

        // Comparator contract: negative = this patch comes before the other, zero = same ordering position,
        // positive = this patch comes after the other.
        return when {
            suffix == other.suffix -> 0
            suffix == null -> -1
            other.suffix == null -> 1
            else -> suffix.compareTo(other.suffix)
        }
    }

    override fun toString(): String = buildString {
        append(compatibilityVersion)
        append('.')
        append(patch)
        suffix?.let(::append)
    }
}
