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
import com.colinmoerbe.poecompanion.league.GamePatch
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Atomically publishes canonical estimates; only lifecycle fields can change after a record is inserted.
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
        snapshot: FixedDropEstimateSnapshot,
        expectedActiveEstimateId: DropEstimateId?,
    ) {
        require(!repository.existsById(snapshot.id.value)) {
            "Drop estimate identity already exists: ${snapshot.id}"
        }

        val patchNumber = snapshot.effectiveFromPatch?.patch
        val patchSuffix = snapshot.effectiveFromPatch?.suffix

        if (expectedActiveEstimateId != null) {
            val transitioned =
                repository.supersedeActiveIfExpected(
                    expectedId = expectedActiveEstimateId.value,
                    relationshipId = dropRelationshipId.value,
                    major = scope.compatibilityVersion.major,
                    minor = scope.compatibilityVersion.minor,
                    ruleset = scope.ruleset.name,
                    leagueId = scope.leagueDefinitionId?.value,
                    patchNumber = patchNumber,
                    patchSuffix = patchSuffix,
                )

            if (transitioned != 1) {
                throw FixedDropEstimatePublicationConflictException(
                    "Expected active fixed drop estimate was not found at this exact probability key",
                )
            }
        }

        try {
            repository.insertActive(
                id = snapshot.id.value,
                relationshipId = dropRelationshipId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
                patchNumber = patchNumber,
                patchSuffix = patchSuffix,
                probability = snapshot.model.probabilityPerAttempt,
                provenance = snapshot.provenance.name,
                evidenceReference = snapshot.evidenceReference,
                reviewedAt = snapshot.reviewedAt,
            )
        } catch (exception: DataIntegrityViolationException) {
            throw translateIntegrityViolation(exception)
        }
    }

    @Transactional(readOnly = true)
    override fun findAcceptedTimeline(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): FixedDropEstimateTimeline? {
        val records =
            repository.findActiveByExactScope(
                relationshipId = dropRelationshipId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
            )
        if (records.isEmpty()) {
            return null
        }

        val relationship =
            requireNotNull(findDropRelationshipById(dropRelationshipId)) {
                "Published drop estimates reference an unknown relationship: $dropRelationshipId"
            }
        val snapshots =
            records.map { entity ->
                FixedDropEstimateSnapshot(
                    id = DropEstimateId(entity.id),
                    effectiveFromPatch =
                    entity.effectivePatchNumber?.let { patchNumber ->
                        GamePatch(scope.compatibilityVersion, patchNumber, entity.effectivePatchSuffix)
                    },
                    model = FixedBernoulliModel(entity.probabilityPerAttempt),
                    provenance = entity.provenance,
                    evidenceReference = entity.evidenceReference,
                    reviewedAt = entity.reviewedAt,
                )
            }

        return FixedDropEstimateTimeline(
            dropRelationship = relationship,
            scope = scope,
            attemptDefinition = CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
            snapshots = snapshots,
        )
    }

    private fun translateIntegrityViolation(exception: DataIntegrityViolationException): RuntimeException =
        if (exception.hasConstraintName(ACTIVE_ESTIMATE_UNIQUE_CONSTRAINT)) {
            FixedDropEstimatePublicationConflictException(
                "Another active fixed drop estimate already occupies this exact probability key",
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
