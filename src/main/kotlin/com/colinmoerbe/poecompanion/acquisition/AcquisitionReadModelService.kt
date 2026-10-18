package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.EvaluationContext
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
    private val accountEvaluationService: AccountEvaluationService,
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
        val account = candidates.accountEvaluation ?: return AcquisitionQueryResult.NoAccountEvaluationSelected
        return AcquisitionQueryResult.Evaluated(
            account,
            uniqueDefinitionId,
            candidates.candidates.map { requireNotNull(it.assessment) },
        )
    }

    /** A relationship's identity is discoverable independently of its contextual applicability. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun findCandidatesForUnique(uniqueDefinitionId: UniqueDefinitionId): AcquisitionUniqueCandidatesResult {
        val selected = accountEvaluationService.getSelected()
        val scopes = selected?.let(::scopesFor)
        val candidates = relationshipPersistenceAdapter.findByUniqueDefinitionId(uniqueDefinitionId).map { relationship ->
            val assessment = if (scopes == null || selected == null) {
                null
            } else {
                val sourceKnowledge = resolveSource(relationship.acquisitionSourceId, scopes)
                val attempts = availableAttemptsService.getAvailableAttempts(
                    selected.accountContextId,
                    relationship.acquisitionSourceId,
                )
                assess(relationship, sourceKnowledge, attempts, scopes)
            }
            AcquisitionSourceRelationshipResult(relationship, assessment)
        }
        return AcquisitionUniqueCandidatesResult(selected, candidates)
    }

    /** Lists every stable source, including sources with no relationships or no selected account. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun findSources(): AcquisitionSourceBrowseResult {
        val selected = accountEvaluationService.getSelected()
        val scopes = selected?.let(::scopesFor)
        val counts = relationshipPersistenceAdapter.findAll().groupingBy { it.acquisitionSourceId }.eachCount()
        val sources = sourcePersistenceAdapter.findAll().map { source ->
            AcquisitionSourceSummary(
                source = source,
                relationshipCount = counts[source.id] ?: 0,
                sourceKnowledge = scopes?.let { resolveSource(source.id, it) },
            )
        }
        return AcquisitionSourceBrowseResult(selected, sources)
    }

    /**
     * The optional attempts argument is a read-only calculation preview, never a persisted override.
     * Stored attempts remain independently available in the result.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun findForSource(sourceId: AcquisitionSourceId, previewAttempts: Int? = null): AcquisitionSourceDetailResult? {
        require(previewAttempts == null || previewAttempts >= 0) { "Preview attempts cannot be negative" }
        val source = sourcePersistenceAdapter.findById(sourceId) ?: return null
        val selected = accountEvaluationService.getSelected()
        val scopes = selected?.let(::scopesFor)
        val sourceKnowledge = scopes?.let { resolveSource(source.id, it) }
        val savedAttempts = selected?.let {
            availableAttemptsService.getAvailableAttempts(it.accountContextId, source.id)
        }
        val effectiveAttempts = if (previewAttempts != null && selected != null) {
            AvailableAttempts(selected.accountContextId, source.id, previewAttempts)
        } else {
            savedAttempts
        }
        val relationships = relationshipPersistenceAdapter.findByAcquisitionSourceId(source.id).map { relationship ->
            AcquisitionSourceRelationshipResult(
                relationship = relationship,
                assessment = if (scopes == null || sourceKnowledge == null) {
                    null
                } else {
                    assess(relationship, sourceKnowledge, savedAttempts, scopes, effectiveAttempts)
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
        )
    }

    private data class KnowledgeScopes(
        val context: EvaluationContext,
        val general: AcquisitionKnowledgeScope,
        val league: AcquisitionKnowledgeScope,
    )

    private fun scopesFor(account: AccountEvaluation): KnowledgeScopes {
        val context = account.evaluationContext
        val selectedLeague = requireNotNull(context.leagueDefinitionId) {
            "The selected account evaluation must identify its league"
        }
        val general = AcquisitionKnowledgeScope(context.compatibilityVersion, context.ruleset)
        return KnowledgeScopes(context, general, general.copy(leagueDefinitionId = selectedLeague))
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
        calculationAttempts: AvailableAttempts? = savedAttempts,
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
                calculationAttempts,
            ),
        )
    }

    /** Preserve independent facts but calculate only when every canonical prerequisite is affirmative. */
    private fun calculateIfApplicable(
        relationship: DropRelationship,
        sourceKnowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>,
        relationshipKnowledge: ScopedAcquisitionKnowledge<DropRelationshipResolution>,
        probabilityKnowledge: ScopedAcquisitionKnowledge<FixedDropEstimateResolution>,
        availableAttempts: AvailableAttempts?,
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
            FixedProbabilityCalculation.from(relationship, acceptedEstimate.model, availableAttempts)
        } else {
            null
        }
    }
}
