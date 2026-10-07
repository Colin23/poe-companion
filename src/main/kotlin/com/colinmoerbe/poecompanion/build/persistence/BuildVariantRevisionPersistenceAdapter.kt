package com.colinmoerbe.poecompanion.build.persistence

import com.colinmoerbe.poecompanion.build.BuildVariantId
import com.colinmoerbe.poecompanion.build.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionStatus
import com.colinmoerbe.poecompanion.build.Requirement
import com.colinmoerbe.poecompanion.build.RequirementGroup
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
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
) {
    @Transactional
    fun save(revision: BuildVariantRevision) {
        revisionRepository.save(
            BuildVariantRevisionEntity(
                id = revision.id.value,
                buildVariantId = revision.buildVariantId.value,
                compatibilityVersionMajor = revision.compatibilityVersion.major,
                compatibilityVersionMinor = revision.compatibilityVersion.minor,
                status = revision.status,
            ),
        )

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
    fun findById(id: BuildVariantRevisionId): BuildVariantRevision? {
        val entity = revisionRepository.findById(id.value).orElse(null) ?: return null
        val requirementEntities = requirementRepository.findAllByRevisionId(id.value)
        val requirementsByGroupPosition = requirementEntities.groupBy { it.id.groupPosition }
        val groups =
            groupRepository.findAllByRevisionId(id.value).map { groupEntity ->
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
}
