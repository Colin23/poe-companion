package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.CompletedBossEncounterAttempt
import com.colinmoerbe.poecompanion.acquisition.DropEstimateId
import com.colinmoerbe.poecompanion.acquisition.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.FixedBernoulliModel
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimatePersistencePort
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimatePublicationConflictException
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimateSnapshot
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimateTimeline
import com.colinmoerbe.poecompanion.acquisition.FixedProbabilityChangePoint
import com.colinmoerbe.poecompanion.acquisition.FixedProbabilityPublicationId
import com.colinmoerbe.poecompanion.acquisition.FixedProbabilityWithdrawal
import com.colinmoerbe.poecompanion.acquisition.FixedProbabilityWithdrawalId
import com.colinmoerbe.poecompanion.league.GamePatch
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Publishes immutable estimate or withdrawal records with one shared ACTIVE exact-key constraint.
 * Only ACTIVE/SUPERSEDED lifecycle fields change after insertion.
 */
@Repository
internal class FixedDropEstimatePersistenceAdapter(
    private val repository: SpringDataFixedDropEstimateRepository,
    private val relationshipPersistenceAdapter: DropRelationshipPersistenceAdapter,
) : FixedDropEstimatePersistencePort {
    override fun findDropRelationshipById(id: DropRelationshipId): DropRelationship? =
        relationshipPersistenceAdapter.findById(id)

    @Transactional
    override fun publish(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        changePoint: FixedProbabilityChangePoint,
        expectedActivePublicationId: FixedProbabilityPublicationId?,
    ) {
        require(!repository.existsById(changePoint.publicationId.value)) {
            "Probability publication identity already exists: ${changePoint.publicationId}"
        }
        if (expectedActivePublicationId != null) {
            supersedeExpected(dropRelationshipId, scope, changePoint, expectedActivePublicationId)
        }
        try {
            when (changePoint) {
                is FixedProbabilityChangePoint.Known -> insertEstimate(dropRelationshipId, scope, changePoint.estimate)

                is FixedProbabilityChangePoint.Withdrawn ->
                    insertWithdrawal(dropRelationshipId, scope, changePoint.withdrawal)
            }
        } catch (exception: DataIntegrityViolationException) {
            throw translateIntegrityViolation(exception)
        }
    }

    private fun supersedeExpected(
        relationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        changePoint: FixedProbabilityChangePoint,
        expected: FixedProbabilityPublicationId,
    ) {
        val transitioned =
            repository.supersedeActiveIfExpected(
                expectedId = expected.value,
                expectedKnowledgeKind = kind(expected).name,
                relationshipId = relationshipId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
                patchNumber = changePoint.effectiveFromPatch?.patch,
                patchSuffix = changePoint.effectiveFromPatch?.suffix,
            )
        if (transitioned != 1) {
            throw FixedDropEstimatePublicationConflictException(
                "Expected active probability publication was not found at this exact change point",
            )
        }
    }

    private fun insertEstimate(
        relationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: FixedDropEstimateSnapshot,
    ) {
        repository.insertActive(
            id = snapshot.id.value,
            relationshipId = relationshipId.value,
            major = scope.compatibilityVersion.major,
            minor = scope.compatibilityVersion.minor,
            ruleset = scope.ruleset.name,
            leagueId = scope.leagueDefinitionId?.value,
            patchNumber = snapshot.effectiveFromPatch?.patch,
            patchSuffix = snapshot.effectiveFromPatch?.suffix,
            probability = snapshot.model.probabilityPerAttempt,
            provenance = snapshot.provenance.name,
            evidenceReference = snapshot.evidenceReference,
            reviewedAt = snapshot.reviewedAt,
        )
    }

    private fun insertWithdrawal(
        relationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        withdrawal: FixedProbabilityWithdrawal,
    ) {
        repository.insertActiveWithdrawal(
            id = withdrawal.id.value,
            relationshipId = relationshipId.value,
            major = scope.compatibilityVersion.major,
            minor = scope.compatibilityVersion.minor,
            ruleset = scope.ruleset.name,
            leagueId = scope.leagueDefinitionId?.value,
            patchNumber = withdrawal.effectiveFromPatch?.patch,
            patchSuffix = withdrawal.effectiveFromPatch?.suffix,
            reason = withdrawal.reason,
            evidenceReference = withdrawal.evidenceReference,
            reviewedAt = withdrawal.reviewedAt,
        )
    }

    @Transactional(readOnly = true)
    override fun findAcceptedTimeline(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): FixedDropEstimateTimeline? {
        val accepted =
            repository.findActiveByExactScope(
                relationshipId = dropRelationshipId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
            )
        if (accepted.isEmpty()) return null

        val relationship =
            requireNotNull(findDropRelationshipById(dropRelationshipId)) {
                "Published probability knowledge references an unknown relationship: $dropRelationshipId"
            }
        return FixedDropEstimateTimeline(
            dropRelationship = relationship,
            scope = scope,
            attemptDefinition = CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
            snapshots = accepted.filter { it.knowledgeKind == FixedProbabilityKnowledgeKind.ESTIMATE }
                .map { toEstimate(it, scope) },
            withdrawals = accepted.filter { it.knowledgeKind == FixedProbabilityKnowledgeKind.WITHDRAWAL }
                .map { toWithdrawal(it, scope) },
        )
    }

    private fun toEstimate(
        entity: FixedDropEstimateEntity,
        scope: AcquisitionKnowledgeScope,
    ): FixedDropEstimateSnapshot = FixedDropEstimateSnapshot(
        id = DropEstimateId(entity.id),
        effectiveFromPatch = entity.effectivePatchNumber?.let { number ->
            GamePatch(scope.compatibilityVersion, number, entity.effectivePatchSuffix)
        },
        model = FixedBernoulliModel(requireNotNull(entity.probabilityPerAttempt)),
        provenance = requireNotNull(entity.provenance),
        evidenceReference = entity.evidenceReference,
        reviewedAt = entity.reviewedAt,
    )

    private fun toWithdrawal(
        entity: FixedDropEstimateEntity,
        scope: AcquisitionKnowledgeScope,
    ): FixedProbabilityWithdrawal = FixedProbabilityWithdrawal(
        id = FixedProbabilityWithdrawalId(entity.id),
        effectiveFromPatch = entity.effectivePatchNumber?.let { number ->
            GamePatch(scope.compatibilityVersion, number, entity.effectivePatchSuffix)
        },
        reason = requireNotNull(entity.withdrawalReason),
        evidenceReference = entity.evidenceReference,
        reviewedAt = entity.reviewedAt,
    )

    private fun kind(id: FixedProbabilityPublicationId): FixedProbabilityKnowledgeKind = when (id) {
        is FixedProbabilityPublicationId.Estimate -> FixedProbabilityKnowledgeKind.ESTIMATE
        is FixedProbabilityPublicationId.Withdrawal -> FixedProbabilityKnowledgeKind.WITHDRAWAL
    }

    private fun translateIntegrityViolation(exception: DataIntegrityViolationException): RuntimeException =
        if (exception.hasConstraintName(ACTIVE_ESTIMATE_UNIQUE_CONSTRAINT)) {
            FixedDropEstimatePublicationConflictException(
                "Another active probability publication already occupies this exact change point",
                exception,
            )
        } else {
            exception
        }

    private fun Throwable.hasConstraintName(constraintName: String): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ConstraintViolationException && current.constraintName == constraintName) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private companion object {
        const val ACTIVE_ESTIMATE_UNIQUE_CONSTRAINT = "uq_fixed_drop_estimate_active_key"
    }
}
