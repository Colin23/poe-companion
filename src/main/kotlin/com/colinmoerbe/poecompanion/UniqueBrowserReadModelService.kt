package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.CurrentOwnership
import com.colinmoerbe.poecompanion.account.CurrentOwnershipService
import com.colinmoerbe.poecompanion.acquisition.AcquisitionReadModelService
import com.colinmoerbe.poecompanion.catalog.UniqueCatalogQueryService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
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
            selectionLabel = selected?.let(::describeSelection),
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
            val assessment = candidate.assessment
            val rate = if (assessment?.calculation == null) {
                null
            } else {
                AcquisitionBrowserKnowledgeLabels.acceptedPerAttemptChance(assessment.probabilityKnowledge)
            }
            UniqueAcquisitionCandidate(
                sourceId = candidate.relationship.acquisitionSourceId.value,
                displayName = AcquisitionBrowserKnowledgeLabels.sourceLabel(assessment?.sourceKnowledge),
                availability = AcquisitionBrowserKnowledgeLabels.sourceAvailability(assessment?.sourceKnowledge),
                relationship = AcquisitionBrowserKnowledgeLabels.relationship(assessment?.relationshipKnowledge),
                estimate = AcquisitionBrowserKnowledgeLabels.estimate(assessment?.probabilityKnowledge),
                actionableChance = rate,
            )
        }.sortedWith(compareBy<UniqueAcquisitionCandidate> { it.displayName.lowercase() }.thenBy { it.sourceId })
        return UniqueDetailPage(
            item = entry(unique.id, ownership),
            accountSelected = selected != null,
            selectionLabel = selected?.let(::describeSelection),
            acquisitionSources = candidates,
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

    private fun describeSelection(selection: AccountEvaluation): String {
        val context = selection.evaluationContext
        return "Account ${selection.accountContextId.value} · " +
            "${context.compatibilityVersion.major}.${context.compatibilityVersion.minor} · " +
            context.ruleset.name
    }
}
