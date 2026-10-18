package com.colinmoerbe.poecompanion

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
    val selectionLabel: String?,
    val totalCount: Int,
    val ownedCount: Int?,
    val missingCount: Int?,
    val filter: String,
)

internal data class UniqueDetailPage(
    val item: UniqueBrowserEntry,
    val accountSelected: Boolean,
    val selectionLabel: String?,
)

