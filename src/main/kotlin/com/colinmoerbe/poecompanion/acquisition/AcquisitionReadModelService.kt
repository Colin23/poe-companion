package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * Discovers candidate sources for a Unique and independently resolves accepted acquisition knowledge.
 *
 * A canonical source-to-Unique identity is not proof of current availability or CAN_DROP.
 * Fixed probability and personalized attempts are resolved separately before any actionable calculation.
 */
@Service
class AcquisitionReadModelService internal constructor(
    private val accountEvaluationService: AccountEvaluationService,
    private val relationshipPersistenceAdapter: DropRelationshipPersistenceAdapter,
    private val bossEncounterKnowledgeService: BossEncounterKnowledgeService,
    private val dropRelationshipKnowledgeService: DropRelationshipKnowledgeService,
    private val fixedDropEstimateService: FixedDropEstimateService,
    private val availableAttemptsService: AvailableAttemptsService,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun findForUnique(uniqueDefinitionId: UniqueDefinitionId): AcquisitionQueryResult {
        val accountEvaluation =
            accountEvaluationService.getSelected() ?: return AcquisitionQueryResult.NoAccountEvaluationSelected

        val context = accountEvaluation.evaluationContext
        val selectedLeague = requireNotNull(context.leagueDefinitionId) {
            "The selected account evaluation must identify its league"
        }
        val generalScope = AcquisitionKnowledgeScope(context.compatibilityVersion, context.ruleset)
        val leagueScope = generalScope.copy(leagueDefinitionId = selectedLeague)

        val assessments =
            relationshipPersistenceAdapter.findByUniqueDefinitionId(uniqueDefinitionId).map { relationship ->
                val sourceKnowledge =
                    resolveScopedAcquisitionKnowledge(
                        bossEncounterKnowledgeService.findTimeline(relationship.acquisitionSourceId, generalScope),
                        bossEncounterKnowledgeService.findTimeline(relationship.acquisitionSourceId, leagueScope),
                    ) { timeline -> timeline.resolve(context) }

                val relationshipKnowledge =
                    resolveScopedAcquisitionKnowledge(
                        dropRelationshipKnowledgeService.findTimeline(relationship.id, generalScope),
                        dropRelationshipKnowledgeService.findTimeline(relationship.id, leagueScope),
                    ) { timeline -> timeline.resolve(context) }

                val probabilityKnowledge =
                    resolveScopedAcquisitionKnowledge(
                        fixedDropEstimateService.findAcceptedTimeline(relationship.id, generalScope),
                        fixedDropEstimateService.findAcceptedTimeline(relationship.id, leagueScope),
                    ) { timeline -> timeline.resolve(context) }

                val availableAttempts =
                    availableAttemptsService.getAvailableAttempts(
                        accountEvaluation.accountContextId,
                        relationship.acquisitionSourceId,
                    )

                // Accepted probability alone never makes an unavailable source or inapplicable drop farmable.
                val sourceAvailable =
                    (sourceKnowledge as? ScopedAcquisitionKnowledge.Selected)?.resolution.let { resolution ->
                        resolution is BossEncounterKnowledgeResolution.Resolved &&
                            resolution.snapshot.availability == AcquisitionSourceAvailability.AVAILABLE
                    }
                val relationshipCanDrop =
                    (relationshipKnowledge as? ScopedAcquisitionKnowledge.Selected)?.resolution.let { resolution ->
                        resolution is DropRelationshipResolution.Resolved &&
                            resolution.snapshot.state == DropRelationshipState.CAN_DROP
                    }
                val acceptedEstimate =
                    ((probabilityKnowledge as? ScopedAcquisitionKnowledge.Selected)?.resolution
                        as? FixedDropEstimateResolution.Resolved)?.snapshot

                val calculation =
                    if (sourceAvailable && relationshipCanDrop && acceptedEstimate != null) {
                        FixedProbabilityCalculation.from(relationship, acceptedEstimate.model, availableAttempts)
                    } else {
                        null
                    }

                AcquisitionSourceAssessment(
                    relationship = relationship,
                    sourceKnowledge = sourceKnowledge,
                    relationshipKnowledge = relationshipKnowledge,
                    probabilityKnowledge = probabilityKnowledge,
                    availableAttempts = availableAttempts,
                    calculation = calculation,
                )
            }

        return AcquisitionQueryResult.Evaluated(accountEvaluation, uniqueDefinitionId, assessments)
    }
}
