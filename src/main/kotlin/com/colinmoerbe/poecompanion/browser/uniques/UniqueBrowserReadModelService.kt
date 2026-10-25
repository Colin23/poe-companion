package com.colinmoerbe.poecompanion.browser.uniques

import com.colinmoerbe.poecompanion.account.CurrentOwnership
import com.colinmoerbe.poecompanion.account.CurrentOwnershipService
import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeResolution
import com.colinmoerbe.poecompanion.acquisition.knowledge.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionReadModelService
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionSourceAssessment
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipResolution
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import com.colinmoerbe.poecompanion.browser.shared.AcquisitionBrowserKnowledgeLabels
import com.colinmoerbe.poecompanion.browser.shared.UniqueDisplayLabels
import com.colinmoerbe.poecompanion.catalog.UniqueCatalogQueryService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.GameEnvironmentOptionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * Web-facing composition of Catalog, selected AccountContext and one CurrentOwnership projection.
 * There is no per-Unique ownership query and no cross-module JPA access.
 */
@Service
internal class UniqueBrowserReadModelService(
    private val catalog: UniqueCatalogQueryService,
    private val accountEvaluation: AccountEvaluationService,
    private val currentOwnership: CurrentOwnershipService,
    private val acquisition: AcquisitionReadModelService,
    private val environments: GameEnvironmentOptionService,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun list(filter: UniqueCollectionFilter): UniqueCollectionPage {
        val selected = accountEvaluation.getSelected()
        val ownership = selected?.let { currentOwnership.getCurrentOwnership(it.accountContextId) }
        val allItems = catalog.findAll().map { entry(it.id, ownership) }
            .sortedWith(compareBy<UniqueBrowserEntry> { it.displayName.lowercase() }.thenBy { it.id })

        val visibleItems = when (filter) {
            UniqueCollectionFilter.ALL -> allItems
            UniqueCollectionFilter.OWNED -> allItems.filter { (it.ownedQuantity ?: 0) > 0 }
            UniqueCollectionFilter.MISSING -> allItems.filter { it.ownedQuantity == 0 }
        }

        return UniqueCollectionPage(
            items = visibleItems,
            accountSelected = selected != null,
            environmentSelected = environments.current() != null,
            selectionLabel = environments.current()?.label,
            totalCount = allItems.size,
            ownedCount = ownership?.let { allItems.count { item -> (item.ownedQuantity ?: 0) > 0 } },
            missingCount = ownership?.let { allItems.count { item -> item.ownedQuantity == 0 } },
            filter = filter.queryValue,
        )
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun detail(id: UniqueDefinitionId): UniqueDetailPage? {
        val unique = catalog.findById(id) ?: return null
        val selected = accountEvaluation.getSelected()
        val ownership = selected?.let { currentOwnership.getCurrentOwnership(it.accountContextId) }
        val candidates = acquisition.findCandidatesForUnique(id).candidates.map { candidate ->
            toCandidate(candidate.relationship.acquisitionSourceId.value, candidate.assessment)
        }.sortedWith(compareBy<UniqueAcquisitionCandidate> { it.displayName.lowercase() }.thenBy { it.sourceId })
            .mapIndexed { index, candidate ->
                if (candidate.nameAvailable) candidate else candidate.copy(unnamedOrdinal = index + 1)
            }
        val environment = environments.current()
        val recognized = selected == null && environment?.context?.let(environments::isRecognized) == true
        return UniqueDetailPage(
            item = entry(unique.id, ownership),
            accountSelected = selected != null,
            environmentSelected = environment != null,
            ownershipEditable = selected != null || recognized,
            selectionLabel = environment?.label,
            acquisitionSources = candidates,
        )
    }

    private fun toCandidate(
        sourceId: java.util.UUID,
        assessment: AcquisitionSourceAssessment?,
    ): UniqueAcquisitionCandidate {
        val sourceKnowledge = assessment?.sourceKnowledge
        val relationshipKnowledge = assessment?.relationshipKnowledge
        val sourceResolution = (sourceKnowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        val relationshipResolution = (relationshipKnowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        val source = sourceResolution as? BossEncounterKnowledgeResolution.Resolved
        val relationship = relationshipResolution as? DropRelationshipResolution.Resolved
        val availability = source?.snapshot?.availability
        val dropState = relationship?.snapshot?.state
        val recordedChance =
            AcquisitionBrowserKnowledgeLabels.acceptedPerAttemptChance(assessment?.probabilityKnowledge)
        val actionableChance = if (assessment?.calculation != null) recordedChance else null
        val ambiguous =
            sourceKnowledge == ScopedAcquisitionKnowledge.Ambiguous ||
                relationshipKnowledge == ScopedAcquisitionKnowledge.Ambiguous

        val statusLabel = when {
            availability == AcquisitionSourceAvailability.UNAVAILABLE -> "Unavailable"
            dropState == DropRelationshipState.CANNOT_DROP -> "Cannot drop"
            dropState == DropRelationshipState.CAN_DROP -> "Can drop"
            ambiguous -> "Ambiguous"
            else -> "Unknown"
        }
        val explanation = when {
            assessment == null -> "Select a game environment to evaluate source availability and drop knowledge."
            availability == AcquisitionSourceAvailability.UNAVAILABLE ->
                "This source is unavailable in the selected environment. Recorded drop knowledge is not actionable."
            dropState == DropRelationshipState.CANNOT_DROP ->
                "This source is recorded as unable to drop this Unique under the selected conditions."
            actionableChance != null ->
                "This source is available, and an accepted fixed estimate applies under the selected conditions."
            ambiguous -> "Accepted knowledge cannot be resolved unambiguously for this environment."
            dropState == DropRelationshipState.CAN_DROP ->
                "The drop relationship is recorded, but no supported actionable fixed chance is available."
            else -> "There is not enough accepted knowledge to determine drop eligibility for this source."
        }

        return UniqueAcquisitionCandidate(
            sourceId = sourceId,
            displayName = AcquisitionBrowserKnowledgeLabels.sourceLabel(sourceKnowledge),
            nameAvailable = source != null,
            unnamedOrdinal = null,
            availabilityLabel = when (availability) {
                AcquisitionSourceAvailability.AVAILABLE -> "Available"
                AcquisitionSourceAvailability.UNAVAILABLE -> "Unavailable"
                null -> AcquisitionBrowserKnowledgeLabels.sourceAvailability(sourceKnowledge)
            },
            relationshipLabel = when (dropState) {
                DropRelationshipState.CAN_DROP -> "Can drop"
                DropRelationshipState.CANNOT_DROP -> "Cannot drop"
                null -> AcquisitionBrowserKnowledgeLabels.relationship(relationshipKnowledge)
            },
            estimateLabel = AcquisitionBrowserKnowledgeLabels.estimate(assessment?.probabilityKnowledge),
            recordedChance = recordedChance,
            actionableChance = actionableChance,
            statusLabel = statusLabel,
            statusCssClass = statusLabel.lowercase().replace(' ', '-'),
            explanation = explanation,
        )
    }

    private fun entry(id: UniqueDefinitionId, ownership: CurrentOwnership?): UniqueBrowserEntry {
        val label = UniqueDisplayLabels.forId(id)
        return UniqueBrowserEntry(
            id = id.value,
            displayName = label.name,
            developmentLabel = label.synthetic,
            ownedQuantity = ownership?.quantityOf(id),
        )
    }
}
