package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.acquisition.AcquisitionReadModelService
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceRelationshipResult
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimateResolution
import com.colinmoerbe.poecompanion.acquisition.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.catalog.UniqueCatalogQueryService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

/** Application-root presentation composition; eligibility and calculation live in Acquisition. */
@Service
internal class AcquisitionBrowserReadModelService(
    private val acquisition: AcquisitionReadModelService,
    private val catalog: UniqueCatalogQueryService,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun list(): AcquisitionBrowserListPage {
        val result = acquisition.findSources()
        val sources = result.sources.map { entry ->
            AcquisitionBrowserSourceEntry(
                id = entry.source.id.value,
                displayName = AcquisitionBrowserKnowledgeLabels.sourceLabel(entry.sourceKnowledge),
                availability = AcquisitionBrowserKnowledgeLabels.sourceAvailability(entry.sourceKnowledge),
                relationshipCount = entry.relationshipCount,
            )
        }.sortedWith(compareBy<AcquisitionBrowserSourceEntry> { it.displayName.lowercase() }.thenBy { it.id })
        return AcquisitionBrowserListPage(
            sources = sources,
            accountSelected = result.accountEvaluation != null,
            selectionLabel = result.accountEvaluation?.let(::describeSelection),
        )
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun detail(sourceId: AcquisitionSourceId, previewAttempts: Int?): AcquisitionBrowserDetailPage? {
        val result = acquisition.findForSource(sourceId, previewAttempts) ?: return null
        val knownIds = catalog.findAll().mapTo(hashSetOf()) { it.id }
        val ordering =
            compareBy<AcquisitionBrowserRelationshipEntry> { it.displayName.lowercase() }.thenBy { it.uniqueId }
        val relationships = result.relationships.map { relationship ->
            toRelationship(relationship, knownIds)
        }.sortedWith(ordering)
        return AcquisitionBrowserDetailPage(
            id = sourceId.value,
            displayName = AcquisitionBrowserKnowledgeLabels.sourceLabel(result.sourceKnowledge),
            availability = AcquisitionBrowserKnowledgeLabels.sourceAvailability(result.sourceKnowledge),
            accountSelected = result.accountEvaluation != null,
            selectionLabel = result.accountEvaluation?.let(::describeSelection),
            savedAttempts = result.availableAttempts?.count,
            previewAttempts = previewAttempts,
            effectiveAttempts = if (result.accountEvaluation == null) null else {
                previewAttempts ?: result.availableAttempts?.count
            },
            relationships = relationships,
        )
    }

    private fun toRelationship(
        result: AcquisitionSourceRelationshipResult,
        knownIds: Set<UniqueDefinitionId>,
    ): AcquisitionBrowserRelationshipEntry {
        val id = result.relationship.uniqueDefinitionId
        val label = UniqueDisplayLabels.forId(id)
        val assessment = result.assessment
        val estimateResolution =
            (assessment?.probabilityKnowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        val estimate = (estimateResolution as? FixedDropEstimateResolution.Resolved)?.snapshot
        val calculation = assessment?.calculation
        val thresholds = if (calculation == null) {
            emptyList()
        } else {
            listOf(
                AcquisitionBrowserThreshold(50, calculation.attemptsFor50Percent),
                AcquisitionBrowserThreshold(75, calculation.attemptsFor75Percent),
                AcquisitionBrowserThreshold(90, calculation.attemptsFor90Percent),
                AcquisitionBrowserThreshold(95, calculation.attemptsFor95Percent),
                AcquisitionBrowserThreshold(99, calculation.attemptsFor99Percent),
            )
        }
        return AcquisitionBrowserRelationshipEntry(
            uniqueId = id.value,
            displayName = label.name,
            catalogKnown = id in knownIds,
            syntheticLabel = label.synthetic,
            relationshipStatus = AcquisitionBrowserKnowledgeLabels.relationship(assessment?.relationshipKnowledge),
            probabilityStatus = AcquisitionBrowserKnowledgeLabels.estimate(assessment?.probabilityKnowledge),
            chancePerAttempt = estimate?.let { percent(it.model.probabilityPerAttempt, PER_ATTEMPT_DECIMALS) },
            estimateProvenance = estimate?.provenance?.name,
            evidenceReference = estimate?.evidenceReference,
            reviewedAt = estimate?.reviewedAt?.toString(),
            actionable = calculation != null,
            probabilityWithinAttempts = calculation?.probabilityWithinAvailableAttempts?.let {
                percent(it, CUMULATIVE_DECIMALS)
            },
            expectedAttempts = calculation?.expectedAttemptsUntilSuccess?.let(::expected),
            thresholds = thresholds,
        )
    }

    private fun percent(fraction: Double, decimals: Int): String =
        String.format(Locale.ROOT, "%." + decimals + "f%%", fraction * PERCENT_MULTIPLIER)

    private fun expected(value: Double): String = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", value)
    }

    private companion object {
        const val PER_ATTEMPT_DECIMALS = 3
        const val CUMULATIVE_DECIMALS = 1
        const val PERCENT_MULTIPLIER = 100.0
    }

    private fun describeSelection(selection: AccountEvaluation): String {
        val context = selection.evaluationContext
        return "Account ${selection.accountContextId.value} · " +
            "${context.compatibilityVersion.major}.${context.compatibilityVersion.minor} · ${context.ruleset.name}"
    }
}
