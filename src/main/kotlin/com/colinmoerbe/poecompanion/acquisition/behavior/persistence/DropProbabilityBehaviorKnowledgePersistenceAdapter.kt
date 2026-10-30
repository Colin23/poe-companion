package com.colinmoerbe.poecompanion.acquisition.behavior.persistence

import com.colinmoerbe.poecompanion.acquisition.behavior.AcceptedDropProbabilityBehaviorChangePoint
import com.colinmoerbe.poecompanion.acquisition.behavior.DropProbabilityBehaviorKnowledgeConflictException
import com.colinmoerbe.poecompanion.acquisition.behavior.DropProbabilityBehaviorSnapshot
import com.colinmoerbe.poecompanion.acquisition.behavior.internal.DropProbabilityBehaviorKnowledgePersistencePort
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.SpringDataDropRelationshipRepository
import com.colinmoerbe.poecompanion.league.GamePatch
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Stores accepted behavior for exact context change points; it never writes numeric estimates or drop relationships.
 */
@Repository
internal class DropProbabilityBehaviorKnowledgePersistenceAdapter(
    private val repository: SpringDataDropProbabilityBehaviorKnowledgeRepository,
    private val relationships: SpringDataDropRelationshipRepository,
) : DropProbabilityBehaviorKnowledgePersistencePort {
    override fun relationshipExists(dropRelationshipId: DropRelationshipId): Boolean =
        relationships.existsById(dropRelationshipId.value)

    @Transactional
    override fun recordSnapshot(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: DropProbabilityBehaviorSnapshot,
        expectedRevision: Long?,
    ): Long {
        if (expectedRevision != null) {
            val updated = repository.replaceIfCurrent(
                relationshipId = dropRelationshipId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
                patchNumber = snapshot.effectiveFromPatch?.patch,
                patchSuffix = snapshot.effectiveFromPatch?.suffix,
                behavior = snapshot.behavior.name,
                expectedRevision = expectedRevision,
            )
            if (updated != 1) {
                throw DropProbabilityBehaviorKnowledgeConflictException(
                    "Expected probability-behavior revision no longer matches this change point",
                )
            }
            return expectedRevision + 1
        }

        try {
            repository.insertAccepted(
                id = UUID.randomUUID(),
                relationshipId = dropRelationshipId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
                patchNumber = snapshot.effectiveFromPatch?.patch,
                patchSuffix = snapshot.effectiveFromPatch?.suffix,
                behavior = snapshot.behavior.name,
            )
        } catch (exception: DataIntegrityViolationException) {
            if (exception.hasConstraintName(CHANGE_POINT_UNIQUE_INDEX)) {
                throw DropProbabilityBehaviorKnowledgeConflictException(
                    "Probability behavior has already been curated for this exact change point",
                    exception,
                )
            }
            throw exception
        }
        return 0
    }

    @Transactional(readOnly = true)
    override fun findAcceptedChangePoints(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedDropProbabilityBehaviorChangePoint> =
        repository.findAcceptedByExactScope(
            relationshipId = dropRelationshipId.value,
            major = scope.compatibilityVersion.major,
            minor = scope.compatibilityVersion.minor,
            ruleset = scope.ruleset.name,
            leagueId = scope.leagueDefinitionId?.value,
        ).map { entity ->
            AcceptedDropProbabilityBehaviorChangePoint(
                snapshot = DropProbabilityBehaviorSnapshot(
                    effectiveFromPatch = entity.effectivePatchNumber?.let { patch ->
                        GamePatch(scope.compatibilityVersion, patch, entity.effectivePatchSuffix)
                    },
                    behavior = entity.behavior,
                ),
                revision = entity.revision,
            )
        }

    private fun Throwable.hasConstraintName(name: String): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ConstraintViolationException && current.constraintName == name) return true
            current = current.cause
        }
        return false
    }

    private companion object {
        const val CHANGE_POINT_UNIQUE_INDEX = "uq_drop_probability_behavior_change_point"
    }
}
