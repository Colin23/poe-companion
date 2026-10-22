package com.colinmoerbe.poecompanion.acquisition.readmodel

import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttempts
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeResolution
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.knowledge.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.knowledge.resolveScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateResolution
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateService
import com.colinmoerbe.poecompanion.acquisition.probability.FixedProbabilityCalculation
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipResolution
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextAccessService
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * Resolves known acquisition identities and independent accepted knowledge for a selected game context.
 *
 * A source identity does not prove availability; a relationship identity does not prove CAN_DROP.
 * Actionable fixed probability requires each of source, relationship and estimate to be affirmative.
 */
@Service
class AcquisitionReadModelService internal constructor(
    private val gameSelection: GameEnvironmentSelectionService,
    private val access: AccountContextAccessService,
    private val sourcePersistenceAdapter: AcquisitionSourcePersistenceAdapter,
    private val relationshipPersistenceAdapter: DropRelationshipPersistenceAdapter,
    private val bossEncounterKnowledgeService: BossEncounterKnowledgeService,
    private val dropRelationshipKnowledgeService: DropRelationshipKnowledgeService,
    private val fixedDropEstimateService: FixedDropEstimateService,
    private val availableAttemptsService: AvailableAttemptsService,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun findForUnique(uniqueDefinitionId: UniqueDefinitionId): AcquisitionQueryResult {
        val candidates = findCandidatesForUnique(uniqueDefinitionId)
        val context = candidates.evaluationContext ?: return AcquisitionQueryResult.NoAccountEvaluationSelected
        return AcquisitionQueryResult.Evaluated(
            candidates.accountEvaluation,
            uniqueDefinitionId,
            candidates.candidates.map { requireNotNull(it.assessment) },
            context,
        )
    }

    /** A relationship's identity is discoverable independently of its contextual applicability. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun findCandidatesForUnique(uniqueDefinitionId: UniqueDefinitionId): AcquisitionUniqueCandidatesResult {
        val context = gameSelection.getSelected()
        val selected = personal(context)
        val scopes = context?.let(::scopesFor)
        val relationships = relationshipPersistenceAdapter.findByUniqueDefinitionId(uniqueDefinitionId)
        val candidates = relationships.map { relationship ->
            val assessment = if (scopes == null) {
                null
            } else {
                val sourceKnowledge = resolveSource(relationship.acquisitionSourceId, scopes)
                val attempts = selected?.let {
                    availableAttemptsService.getAvailableAttempts(it.accountContextId, relationship.acquisitionSourceId)
                }
                assess(relationship, sourceKnowledge, attempts, scopes)
            }
            AcquisitionSourceRelationshipResult(relationship, assessment)
        }
        return AcquisitionUniqueCandidatesResult(selected, candidates, context)
    }

    /** Lists every stable source, including sources with no relationships or no selected account. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun findSources(): AcquisitionSourceBrowseResult {
        val context = gameSelection.getSelected()
        val selected = personal(context)
        val scopes = context?.let(::scopesFor)
        val counts = relationshipPersistenceAdapter.findAll().groupingBy { it.acquisitionSourceId }.eachCount()
        val sources = sourcePersistenceAdapter.findAll().map { source ->
            AcquisitionSourceSummary(
                source = source,
                relationshipCount = counts[source.id] ?: 0,
                sourceKnowledge = scopes?.let { resolveSource(source.id, it) },
            )
        }
        return AcquisitionSourceBrowseResult(selected, sources, context)
    }

    /**
     * The optional attempts argument is a read-only calculation preview, never a persisted override.
     * Stored attempts remain independently available in the result.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun findForSource(sourceId: AcquisitionSourceId, previewAttempts: Int? = null): AcquisitionSourceDetailResult? {
        require(previewAttempts == null || previewAttempts >= 0) { "Preview attempts cannot be negative" }
        val source = sourcePersistenceAdapter.findById(sourceId) ?: return null
        val context = gameSelection.getSelected()
        val selected = personal(context)
        val scopes = context?.let(::scopesFor)
        val sourceKnowledge = scopes?.let { resolveSource(source.id, it) }
        val savedAttempts = selected?.let {
            availableAttemptsService.getAvailableAttempts(it.accountContextId, source.id)
        }
        val effectiveAttemptCount = previewAttempts ?: savedAttempts?.count
        val relationships = relationshipPersistenceAdapter.findByAcquisitionSourceId(source.id).map { relationship ->
            AcquisitionSourceRelationshipResult(
                relationship = relationship,
                assessment = if (scopes == null || sourceKnowledge == null) {
                    null
                } else {
                    assess(relationship, sourceKnowledge, savedAttempts, scopes, effectiveAttemptCount)
                },
            )
        }
        return AcquisitionSourceDetailResult(
            source = source,
            accountEvaluation = selected,
            sourceKnowledge = sourceKnowledge,
            availableAttempts = savedAttempts,
            previewAttempts = previewAttempts,
            relationships = relationships,
            evaluationContext = context,
        )
    }

    private data class KnowledgeScopes(
        val context: EvaluationContext,
        val general: AcquisitionKnowledgeScope,
        val league: AcquisitionKnowledgeScope,
    )

    private fun scopesFor(context: EvaluationContext): KnowledgeScopes {
        val selectedLeague = requireNotNull(context.leagueDefinitionId) {
            "The selected account evaluation must identify its league"
        }
        val general = AcquisitionKnowledgeScope(context.compatibilityVersion, context.ruleset)
        return KnowledgeScopes(context, general, general.copy(leagueDefinitionId = selectedLeague))
    }

    private fun personal(context: EvaluationContext?): AccountEvaluation? = context?.let { selected ->
        selected.leagueDefinitionId?.let { access.findOwned(it, selected.ruleset) }
            ?.let { AccountEvaluation(it.id, selected) }
    }

    private fun resolveSource(
        id: AcquisitionSourceId,
        scopes: KnowledgeScopes,
    ): ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution> = resolveScopedAcquisitionKnowledge(
        bossEncounterKnowledgeService.findTimeline(id, scopes.general),
        bossEncounterKnowledgeService.findTimeline(id, scopes.league),
    ) { timeline -> timeline.resolve(scopes.context) }

    private fun assess(
        relationship: DropRelationship,
        sourceKnowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>,
        savedAttempts: AvailableAttempts?,
        scopes: KnowledgeScopes,
        calculationAttemptCount: Int? = savedAttempts?.count,
    ): AcquisitionSourceAssessment {
        val relationshipKnowledge = resolveScopedAcquisitionKnowledge(
            dropRelationshipKnowledgeService.findTimeline(relationship.id, scopes.general),
            dropRelationshipKnowledgeService.findTimeline(relationship.id, scopes.league),
        ) { timeline -> timeline.resolve(scopes.context) }
        val probabilityKnowledge = resolveScopedAcquisitionKnowledge(
            fixedDropEstimateService.findAcceptedTimeline(relationship.id, scopes.general),
            fixedDropEstimateService.findAcceptedTimeline(relationship.id, scopes.league),
        ) { timeline -> timeline.resolve(scopes.context) }

        return AcquisitionSourceAssessment(
            relationship = relationship,
            sourceKnowledge = sourceKnowledge,
            relationshipKnowledge = relationshipKnowledge,
            probabilityKnowledge = probabilityKnowledge,
            availableAttempts = savedAttempts,
            calculation = calculateIfApplicable(
                relationship,
                sourceKnowledge,
                relationshipKnowledge,
                probabilityKnowledge,
                calculationAttemptCount,
            ),
        )
    }

    /** Preserve independent facts but calculate only when every canonical prerequisite is affirmative. */
    private fun calculateIfApplicable(
        relationship: DropRelationship,
        sourceKnowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>,
        relationshipKnowledge: ScopedAcquisitionKnowledge<DropRelationshipResolution>,
        probabilityKnowledge: ScopedAcquisitionKnowledge<FixedDropEstimateResolution>,
        attemptCount: Int?,
    ): FixedProbabilityCalculation? {
        val sourceResolution = (sourceKnowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        val dropResolution = (relationshipKnowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution
        val estimateResolution = (probabilityKnowledge as? ScopedAcquisitionKnowledge.Selected<*>)?.resolution

        val sourceAvailable =
            sourceResolution is BossEncounterKnowledgeResolution.Resolved &&
                sourceResolution.snapshot.availability == AcquisitionSourceAvailability.AVAILABLE
        val relationshipCanDrop =
            dropResolution is DropRelationshipResolution.Resolved &&
                dropResolution.snapshot.state == DropRelationshipState.CAN_DROP
        val acceptedEstimate = (estimateResolution as? FixedDropEstimateResolution.Resolved)?.snapshot

        return if (sourceAvailable && relationshipCanDrop && acceptedEstimate != null) {
            FixedProbabilityCalculation.forCount(relationship, acceptedEstimate.model, attemptCount)
        } else {
            null
        }
    }
}
