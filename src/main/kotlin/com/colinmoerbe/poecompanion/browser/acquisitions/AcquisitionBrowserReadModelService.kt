package com.colinmoerbe.poecompanion.browser.acquisitions

import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeResolution
import com.colinmoerbe.poecompanion.acquisition.knowledge.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateResolution
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionReadModelService
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionSourceRelationshipResult
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionSourceAssessment
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipResolution
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.browser.shared.AcquisitionBrowserKnowledgeLabels
import com.colinmoerbe.poecompanion.browser.shared.UniqueDisplayLabels
import com.colinmoerbe.poecompanion.catalog.UniqueCatalogQueryService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.GameEnvironmentOptionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

/** Browser-owned presentation composition; eligibility and calculation live in Acquisition. */
@Service
internal class AcquisitionBrowserReadModelService(
    private val acquisition: AcquisitionReadModelService,
    private val catalog: UniqueCatalogQueryService,
    private val environments: GameEnvironmentOptionService,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun list(): AcquisitionBrowserListPage {
        val result = acquisition.findSources()
        val sources = result.sources.map { entry ->
            val knowledge = entry.sourceKnowledge
            AcquisitionBrowserSourceEntry(
                id = entry.source.id.value,
                displayName = AcquisitionBrowserKnowledgeLabels.sourceLabel(knowledge),
                nameResolved = AcquisitionBrowserKnowledgeLabels.sourceNameResolved(knowledge),
                nameExplanation = AcquisitionBrowserKnowledgeLabels.sourceNameExplanation(knowledge),
                availability = AcquisitionBrowserKnowledgeLabels.readableSourceAvailability(knowledge),
                availabilityCssClass = AcquisitionBrowserKnowledgeLabels.sourceAvailabilityCssClass(knowledge),
                relationshipCount = entry.relationshipCount,
            )
        }.sortedWith(compareBy<AcquisitionBrowserSourceEntry> { it.displayName.lowercase() }.thenBy { it.id })
            .mapIndexed { index, entry ->
                if (entry.nameResolved) entry else entry.copy(displayName = "Acquisition source ${index + 1}")
            }
        return AcquisitionBrowserListPage(
            sources = sources,
            accountSelected = result.accountEvaluation != null,
            environmentSelected = result.evaluationContext != null,
            selectionLabel = environments.current()?.label,
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
            displayName = if (AcquisitionBrowserKnowledgeLabels.sourceNameResolved(result.sourceKnowledge)) {
                AcquisitionBrowserKnowledgeLabels.sourceLabel(result.sourceKnowledge)
            } else {
                "Acquisition source"
            },
            nameResolved = AcquisitionBrowserKnowledgeLabels.sourceNameResolved(result.sourceKnowledge),
            nameExplanation = AcquisitionBrowserKnowledgeLabels.sourceNameExplanation(result.sourceKnowledge),
            availability = AcquisitionBrowserKnowledgeLabels.readableSourceAvailability(result.sourceKnowledge),
            availabilityCssClass = AcquisitionBrowserKnowledgeLabels.sourceAvailabilityCssClass(result.sourceKnowledge),
            accountSelected = result.accountEvaluation != null,
            environmentSelected = result.evaluationContext != null,
            attemptsEditable = result.evaluationContext?.let { context ->
                result.accountEvaluation != null || environments.isRecognized(context)
            } == true,
            selectionLabel = environments.current()?.label,
            savedAttempts = result.availableAttempts?.count,
            previewAttempts = previewAttempts,
            effectiveAttempts = if (result.evaluationContext == null) {
                null
            } else {
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
            relationshipStatus = AcquisitionBrowserKnowledgeLabels.readableRelationship(assessment?.relationshipKnowledge),
            relationshipCssClass = AcquisitionBrowserKnowledgeLabels.relationshipCssClass(assessment?.relationshipKnowledge),
            probabilityStatus = AcquisitionBrowserKnowledgeLabels.estimate(assessment?.probabilityKnowledge),
            chancePerAttempt =
            AcquisitionBrowserKnowledgeLabels.acceptedPerAttemptChance(assessment?.probabilityKnowledge),
            estimateProvenance = estimate?.provenance?.name?.lowercase(Locale.ROOT)
                ?.replaceFirstChar { it.titlecase(Locale.ROOT) },
            evidenceReference = estimate?.evidenceReference,
            reviewedAt = estimate?.reviewedAt?.toString(),
            actionable = calculation != null,
            probabilityWithinAttempts = calculation?.probabilityWithinAvailableAttempts?.let {
                AcquisitionBrowserKnowledgeLabels.cumulativePercent(it)
            },
            expectedAttempts = calculation?.expectedAttemptsUntilSuccess?.let(::expected),
            thresholds = thresholds,
            nonActionableExplanation = nonActionableExplanation(assessment),
        )
    }

    private fun nonActionableExplanation(assessment: AcquisitionSourceAssessment?): String? {
        if (assessment?.calculation != null) return null
        if (assessment == null) return "Select a game environment to evaluate this drop relationship."

        val source = (assessment.sourceKnowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        val relationship = (assessment.relationshipKnowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        val availability = (source as? BossEncounterKnowledgeResolution.Resolved)?.snapshot?.availability
        val dropState = (relationship as? DropRelationshipResolution.Resolved)?.snapshot?.state
        return when {
            availability == AcquisitionSourceAvailability.UNAVAILABLE ->
                "This source is unavailable in the selected environment. Recorded estimates are not actionable."
            dropState == DropRelationshipState.CANNOT_DROP ->
                "This source is recorded as unable to drop this Unique in the selected environment."
            availability != AcquisitionSourceAvailability.AVAILABLE ->
                "Source availability is not verified for the selected environment."
            dropState != DropRelationshipState.CAN_DROP ->
                "This drop relationship is not verified for the selected environment."
            else -> "No supported applicable fixed-probability estimate is available."
        }
    }

    private fun expected(value: Double): String = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", value)
    }
}
