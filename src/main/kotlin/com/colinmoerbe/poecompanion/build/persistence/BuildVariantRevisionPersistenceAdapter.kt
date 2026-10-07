package com.colinmoerbe.poecompanion.build.persistence

import com.colinmoerbe.poecompanion.build.BuildRevisionActivationConflictException
import com.colinmoerbe.poecompanion.build.BuildVariantId
import com.colinmoerbe.poecompanion.build.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionPersistencePort
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionStatus
import com.colinmoerbe.poecompanion.build.Requirement
import com.colinmoerbe.poecompanion.build.RequirementGroup
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Persists complete build-revision semantic snapshots without exposing JPA representations to the domain.
 */
@Repository
internal class BuildVariantRevisionPersistenceAdapter(
    private val revisionRepository: SpringDataBuildVariantRevisionRepository,
    private val groupRepository: SpringDataRequirementGroupRepository,
    private val requirementRepository: SpringDataRequirementRepository,
) : BuildVariantRevisionPersistencePort {
    @field:PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional
    fun saveDraftSnapshot(revision: BuildVariantRevision) {
        check(revision.status == BuildVariantRevisionStatus.DRAFT) {
            "Only draft build revisions can persist editable requirement snapshots"
        }

        if (revisionRepository.existsById(revision.id.value)) {
            claimPersistedDraft(revision.id)
        } else {
            revisionRepository.save(
                BuildVariantRevisionEntity(
                    id = revision.id.value,
                    buildVariantId = revision.buildVariantId.value,
                    compatibilityVersionMajor = revision.compatibilityVersion.major,
                    compatibilityVersionMinor = revision.compatibilityVersion.minor,
                    status = revision.status,
                ),
            )
        }

        detachExistingSnapshot(revision.id)
        requirementRepository.deleteAllByRevisionId(revision.id.value)
        groupRepository.deleteAllByRevisionId(revision.id.value)

        val groups =
            revision.requirementGroups.mapIndexed { groupPosition, group ->
                RequirementGroupEntity(
                    id =
                    RequirementGroupEntityId(
                        buildVariantRevisionId = revision.id.value,
                        position = groupPosition,
                    ),
                    importance = group.importance,
                    logic = group.logic,
                )
            }
        groupRepository.saveAll(groups)

        val requirements =
            revision.requirementGroups.flatMapIndexed { groupPosition, group ->
                group.requirements.mapIndexed { requirementPosition, requirement ->
                    RequirementEntity(
                        id =
                        RequirementEntityId(
                            buildVariantRevisionId = revision.id.value,
                            groupPosition = groupPosition,
                            position = requirementPosition,
                        ),
                        uniqueDefinitionId = requirement.uniqueDefinitionId.value,
                        requiredQuantity = requirement.requiredQuantity,
                    )
                }
            }
        requirementRepository.saveAll(requirements)
    }

    @Transactional(readOnly = true)
    override fun findById(id: BuildVariantRevisionId): BuildVariantRevision? {
        val entity = revisionRepository.findById(id.value).orElse(null) ?: return null
        return toDomain(entity)
    }

    @Transactional(readOnly = true)
    override fun findActiveBy(
        buildVariantId: BuildVariantId,
        compatibilityVersion: CompatibilityVersion,
    ): BuildVariantRevision? =
        revisionRepository
            .findByScopeAndStatus(
                buildVariantId = buildVariantId.value,
                compatibilityVersionMajor = compatibilityVersion.major,
                compatibilityVersionMinor = compatibilityVersion.minor,
                status = BuildVariantRevisionStatus.ACTIVE,
            )?.let(::toDomain)

    @Transactional
    override fun transitionStatus(
        id: BuildVariantRevisionId,
        expectedStatus: BuildVariantRevisionStatus,
        newStatus: BuildVariantRevisionStatus,
    ) {
        try {
            val updatedRows =
                revisionRepository.updateStatusIfCurrent(
                    id = id.value,
                    expectedStatus = expectedStatus,
                    newStatus = newStatus,
                )
            if (updatedRows != 1) {
                throw activationConflict(id)
            }
        } catch (exception: DataIntegrityViolationException) {
            throw translateIntegrityViolation(id, exception)
        }
    }

    private fun claimPersistedDraft(id: BuildVariantRevisionId) {
        val claimedRows =
            revisionRepository.updateStatusIfCurrent(
                id = id.value,
                expectedStatus = BuildVariantRevisionStatus.DRAFT,
                newStatus = BuildVariantRevisionStatus.DRAFT,
            )
        check(claimedRows == 1) {
            "Only persisted draft build revisions can replace requirement snapshots"
        }
    }

    private fun toDomain(entity: BuildVariantRevisionEntity): BuildVariantRevision {
        val requirementEntities = requirementRepository.findAllByRevisionId(entity.id)
        val requirementsByGroupPosition = requirementEntities.groupBy { it.id.groupPosition }
        val groups =
            groupRepository.findAllByRevisionId(entity.id).map { groupEntity ->
                RequirementGroup(
                    importance = groupEntity.importance,
                    logic = groupEntity.logic,
                    requirements =
                    requirementsByGroupPosition[groupEntity.id.position]
                        .orEmpty()
                        .map(::toRequirement),
                )
            }

        return BuildVariantRevision(
            id = BuildVariantRevisionId(entity.id),
            buildVariantId = BuildVariantId(entity.buildVariantId),
            compatibilityVersion =
            CompatibilityVersion(
                major = entity.compatibilityVersionMajor,
                minor = entity.compatibilityVersionMinor,
            ),
        ).also { revision ->
            revision.updateRequirementGroups(groups)
            restoreStatus(revision, entity.status)
        }
    }

    private fun detachExistingSnapshot(revisionId: BuildVariantRevisionId) {
        entityManager.flush()
        requirementRepository.findAllByRevisionId(revisionId.value).forEach(entityManager::detach)
        groupRepository.findAllByRevisionId(revisionId.value).forEach(entityManager::detach)
    }

    private fun toRequirement(entity: RequirementEntity): Requirement = Requirement(
        uniqueDefinitionId = UniqueDefinitionId(entity.uniqueDefinitionId),
        requiredQuantity = entity.requiredQuantity,
    )

    private fun restoreStatus(revision: BuildVariantRevision, status: BuildVariantRevisionStatus) {
        when (status) {
            BuildVariantRevisionStatus.DRAFT -> Unit

            BuildVariantRevisionStatus.ACTIVE -> revision.activate()

            BuildVariantRevisionStatus.SUPERSEDED -> {
                revision.activate()
                revision.supersede()
            }
        }
    }

    private fun translateIntegrityViolation(
        id: BuildVariantRevisionId,
        exception: DataIntegrityViolationException,
    ): RuntimeException =
        if (exception.hasConstraintName(ACTIVE_REVISION_UNIQUE_CONSTRAINT)) {
            activationConflict(id, exception)
        } else {
            exception
        }

    private fun activationConflict(
        id: BuildVariantRevisionId,
        cause: Throwable? = null,
    ): BuildRevisionActivationConflictException =
        BuildRevisionActivationConflictException(
            message = "Build revision activation conflicted with concurrent lifecycle state: $id",
            cause = cause,
        )

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
        const val ACTIVE_REVISION_UNIQUE_CONSTRAINT = "uq_build_variant_revision_active_version"
    }
}
