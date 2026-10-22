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
)

internal data class UniqueCollectionPage(
    val items: List<UniqueBrowserEntry>,
    val accountSelected: Boolean,
    val environmentSelected: Boolean,
    val selectionLabel: String?,
    val totalCount: Int,
    val ownedCount: Int?,
    val missingCount: Int?,
    val filter: String,
)

internal data class UniqueAcquisitionCandidate(
    val sourceId: UUID,
    val displayName: String,
    val availability: String,
    val relationship: String,
    val estimate: String,
    val actionableChance: String?,
)

internal data class UniqueDetailPage(
    val item: UniqueBrowserEntry,
    val accountSelected: Boolean,
    val environmentSelected: Boolean,
    val selectionLabel: String?,
    val acquisitionSources: List<UniqueAcquisitionCandidate>,
)
