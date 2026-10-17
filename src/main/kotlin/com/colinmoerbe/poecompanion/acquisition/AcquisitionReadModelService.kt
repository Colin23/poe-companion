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
 * Probability and personalized available-attempt composition are separate, later read-model work.
 */
@Service
class AcquisitionReadModelService internal constructor(
    private val accountEvaluationService: AccountEvaluationService,
    private val relationshipPersistenceAdapter: DropRelationshipPersistenceAdapter,
    private val bossEncounterKnowledgeService: BossEncounterKnowledgeService,
    private val dropRelationshipKnowledgeService: DropRelationshipKnowledgeService,
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

                AcquisitionSourceAssessment(relationship, sourceKnowledge, relationshipKnowledge)
            }

        return AcquisitionQueryResult.Evaluated(accountEvaluation, uniqueDefinitionId, assessments)
    }
}
