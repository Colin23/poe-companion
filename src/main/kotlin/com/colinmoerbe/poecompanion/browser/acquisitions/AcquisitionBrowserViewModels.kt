package com.colinmoerbe.poecompanion.browser.acquisitions

import java.util.UUID

internal data class AcquisitionBrowserSourceEntry(
    val id: UUID,
    val displayName: String,
    val nameResolved: Boolean,
    val nameExplanation: String?,
    val availability: String,
    val availabilityCssClass: String,
    val relationshipCount: Int,
)

internal data class AcquisitionBrowserListPage(
    val sources: List<AcquisitionBrowserSourceEntry>,
    val accountSelected: Boolean,
    val environmentSelected: Boolean,
    val selectionLabel: String?,
)

internal data class AcquisitionBrowserThreshold(val percentage: Int, val attempts: Int?)

internal data class AcquisitionBrowserRelationshipEntry(
    val uniqueId: UUID,
    val displayName: String,
    val catalogKnown: Boolean,
    val syntheticLabel: Boolean,
    val relationshipStatus: String,
    val relationshipCssClass: String,
    val probabilityStatus: String,
    val chancePerAttempt: String?,
    val estimateProvenance: String?,
    val evidenceReference: String?,
    val reviewedAt: String?,
    val actionable: Boolean,
    val probabilityWithinAttempts: String?,
    val expectedAttempts: String?,
    val thresholds: List<AcquisitionBrowserThreshold>,
    val nonActionableExplanation: String?,
) {
    val attemptsFor95Percent: Int?
        get() = thresholds.firstOrNull { it.percentage == 95 }?.attempts
}

internal data class AcquisitionBrowserDetailPage(
    val id: UUID,
    val displayName: String,
    val nameResolved: Boolean,
    val nameExplanation: String?,
    val availability: String,
    val availabilityCssClass: String,
    val accountSelected: Boolean,
    val environmentSelected: Boolean,
    val attemptsEditable: Boolean,
    val selectionLabel: String?,
    val savedAttempts: Int?,
    val previewAttempts: Int?,
    val effectiveAttempts: Int?,
    val relationships: List<AcquisitionBrowserRelationshipEntry>,
) {
    val hasSyntheticLabels: Boolean
        get() = relationships.any { it.syntheticLabel }
}
