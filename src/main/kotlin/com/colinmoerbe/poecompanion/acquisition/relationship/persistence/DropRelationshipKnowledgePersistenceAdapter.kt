package com.colinmoerbe.poecompanion.acquisition.relationship.persistence

import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.AcceptedDropRelationshipChangePoint
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipKnowledgeConflictException
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.relationship.internal.DropRelationshipKnowledgePersistencePort
import com.colinmoerbe.poecompanion.league.GamePatch
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Stores one currently accepted relationship fact for each exact scope and game-patch change point.
 *
 * A conflicting correction is rejected; curator edit history is intentionally not persisted in this increment.
 */
@Repository
internal class DropRelationshipKnowledgePersistenceAdapter(
    private val repository: SpringDataDropRelationshipKnowledgeRepository,
    private val relationshipRepository: SpringDataDropRelationshipRepository,
) : DropRelationshipKnowledgePersistencePort {
    override fun relationshipExists(id: DropRelationshipId): Boolean = relationshipRepository.existsById(id.value)

    @Transactional
    override fun recordSnapshot(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: DropRelationshipSnapshot,
        expectedRevision: Long?,
    ): Long {
        if (expectedRevision != null) {
            val affected =
                repository.replaceIfCurrent(
                    relationshipId = dropRelationshipId.value,
                    major = scope.compatibilityVersion.major,
                    minor = scope.compatibilityVersion.minor,
                    ruleset = scope.ruleset.name,
                    leagueId = scope.leagueDefinitionId?.value,
                    patchNumber = snapshot.effectiveFromPatch?.patch,
                    patchSuffix = snapshot.effectiveFromPatch?.suffix,
                    state = snapshot.state.name,
                    expectedRevision = expectedRevision,
                )
            if (affected != 1) {
                throw DropRelationshipKnowledgeConflictException(
                    "Expected relationship knowledge revision no longer matches this exact change point",
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
                state = snapshot.state.name,
            )
        } catch (exception: DataIntegrityViolationException) {
            throw translateIntegrityViolation(exception)
        }

        return 0
    }

    @Transactional(readOnly = true)
    override fun findAcceptedChangePoints(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedDropRelationshipChangePoint> {
        val entities =
            repository.findAcceptedByExactScope(
                relationshipId = dropRelationshipId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
            )
        return entities.map { entity -> toDomain(entity, scope) }
    }

    private fun toDomain(
        entity: DropRelationshipKnowledgeEntity,
        scope: AcquisitionKnowledgeScope,
    ): AcceptedDropRelationshipChangePoint = AcceptedDropRelationshipChangePoint(
        snapshot =
        DropRelationshipSnapshot(
            effectiveFromPatch =
            entity.effectivePatchNumber?.let { patchNumber ->
                GamePatch(scope.compatibilityVersion, patchNumber, entity.effectivePatchSuffix)
            },
            state = entity.state,
        ),
        revision = entity.revision,
    )

    private fun translateIntegrityViolation(exception: DataIntegrityViolationException): RuntimeException =
        if (exception.hasConstraintName(CHANGE_POINT_UNIQUE_CONSTRAINT)) {
            DropRelationshipKnowledgeConflictException(
                "Another accepted relationship fact already exists for this exact change point",
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
        const val CHANGE_POINT_UNIQUE_CONSTRAINT = "uq_drop_relationship_knowledge_change_point"
    }
}
