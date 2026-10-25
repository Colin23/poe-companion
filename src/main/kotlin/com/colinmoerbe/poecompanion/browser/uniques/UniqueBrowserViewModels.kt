package com.colinmoerbe.poecompanion.browser.uniques

import java.util.UUID

/** Ownership filters apply to the selected account, never to catalog identity or item availability. */
internal enum class UniqueCollectionFilter(val queryValue: String) {
    ALL("all"),
    OWNED("owned"),
    MISSING("missing"),
    ;

    companion object {
        fun parse(value: String): UniqueCollectionFilter? = entries.firstOrNull { it.queryValue == value }
    }
}

internal data class UniqueBrowserEntry(
    val id: UUID,
    val displayName: String,
    val developmentLabel: Boolean,
    val ownedQuantity: Int?,
) {
    val ownershipLabel: String
        get() = when {
            ownedQuantity == null -> "Ownership unknown"
            ownedQuantity == 0 -> "Missing"
            else -> "Owned"
        }

    val ownershipCssClass: String
        get() = when {
            ownedQuantity == null -> "unknown"
            ownedQuantity == 0 -> "missing"
            else -> "owned"
        }

    val quantityLabel: String
        get() = when (val quantity = ownedQuantity) {
            null -> "No personal collection"
            0 -> "0 copies owned"
            1 -> "1 copy owned"
            else -> "$quantity copies owned"
        }
}

internal data class UniqueCollectionPage(
    val items: List<UniqueBrowserEntry>,
    val accountSelected: Boolean,
    val environmentSelected: Boolean,
    val selectionLabel: String?,
    val totalCount: Int,
    val ownedCount: Int?,
    val missingCount: Int?,
    val filter: String,
) {
    val hasDevelopmentLabels: Boolean
        get() = items.any { it.developmentLabel }
}

/** Presentation-only state, independent of canonical acquisition identities. */
internal data class UniqueAcquisitionCandidate(
    val sourceId: UUID,
    val displayName: String,
    val nameAvailable: Boolean,
    val unnamedOrdinal: Int?,
    val availabilityLabel: String,
    val relationshipLabel: String,
    val estimateLabel: String,
    val recordedChance: String?,
    val actionableChance: String?,
    val statusLabel: String,
    val statusCssClass: String,
    val explanation: String,
) {
    val linkLabel: String
        get() = if (nameAvailable) displayName else "Unnamed source ${unnamedOrdinal ?: 1}"
}

internal data class UniqueDetailPage(
    val item: UniqueBrowserEntry,
    val accountSelected: Boolean,
    val environmentSelected: Boolean,
    val ownershipEditable: Boolean,
    val selectionLabel: String?,
    val acquisitionSources: List<UniqueAcquisitionCandidate>,
)
