package com.colinmoerbe.poecompanion.acquisition.encounter.persistence

import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgePersistencePort
import com.colinmoerbe.poecompanion.acquisition.encounter.CorruptBossEncounterKnowledgeException
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataAcquisitionSourceRepository
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataBossDefinitionRepository
import com.colinmoerbe.poecompanion.acquisition.AcceptedBossEncounterChangePoint
import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeConflictException
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.BossParticipation
import com.colinmoerbe.poecompanion.league.GamePatch
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Stores complete accepted snapshots and participant sets atomically using Spring Data JPA.
 *
 * JPA fetch joins read every parent and its roster in one consistent SQL statement.
 * Native repository writes protect exact-key uniqueness and optimistic revisions.
 */
@Repository
internal class BossEncounterKnowledgePersistenceAdapter(
    private val knowledgeRepository: SpringDataBossEncounterKnowledgeRepository,
    private val sourceRepository: SpringDataAcquisitionSourceRepository,
    private val bossRepository: SpringDataBossDefinitionRepository,
) : BossEncounterKnowledgePersistencePort {
    override fun sourceExists(id: AcquisitionSourceId): Boolean = sourceRepository.existsById(id.value)

    override fun bossDefinitionsExist(ids: Set<BossDefinitionId>): Boolean =
        bossRepository.findAllById(ids.map { it.value }).count() == ids.size

    @Transactional
    override fun recordSnapshot(
        acquisitionSourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
        snapshot: BossEncounterKnowledgeSnapshot,
        expectedRevision: Long?,
    ): Long {
        val knowledgeId =
            if (expectedRevision == null) {
                publishFirstChangePoint(acquisitionSourceId, scope, snapshot)
            } else {
                correctAcceptedChangePoint(acquisitionSourceId, scope, snapshot, expectedRevision)
            }
        replaceParticipants(knowledgeId, snapshot.bossParticipation)
        return expectedRevision?.plus(1) ?: 0
    }

    private fun publishFirstChangePoint(
        sourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
        snapshot: BossEncounterKnowledgeSnapshot,
    ): UUID {
        val id = UUID.randomUUID()
        try {
            knowledgeRepository.insertAccepted(
                id = id,
                sourceId = sourceId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
                patchNumber = snapshot.effectiveFromPatch?.patch,
                patchSuffix = snapshot.effectiveFromPatch?.suffix,
                label = snapshot.displayLabel,
                availability = snapshot.availability.name,
                participationKind = kind(snapshot.bossParticipation).name,
            )
        } catch (exception: DataIntegrityViolationException) {
            throw translateIntegrityViolation(exception)
        }
        return id
    }

    private fun correctAcceptedChangePoint(
        sourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
        snapshot: BossEncounterKnowledgeSnapshot,
        expectedRevision: Long,
    ): UUID {
        val id =
            knowledgeRepository.findAcceptedId(
                sourceId = sourceId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
                patchNumber = snapshot.effectiveFromPatch?.patch,
                patchSuffix = snapshot.effectiveFromPatch?.suffix,
            ) ?: throw BossEncounterKnowledgeConflictException(
                "No accepted encounter knowledge exists at this exact change point",
            )

        val updated =
            knowledgeRepository.replaceIfCurrent(
                sourceId = sourceId.value,
                major = scope.compatibilityVersion.major,
                minor = scope.compatibilityVersion.minor,
                ruleset = scope.ruleset.name,
                leagueId = scope.leagueDefinitionId?.value,
                patchNumber = snapshot.effectiveFromPatch?.patch,
                patchSuffix = snapshot.effectiveFromPatch?.suffix,
                label = snapshot.displayLabel,
                availability = snapshot.availability.name,
                participationKind = kind(snapshot.bossParticipation).name,
                expectedRevision = expectedRevision,
            )
        if (updated != 1) {
            throw BossEncounterKnowledgeConflictException(
                "Expected encounter knowledge revision no longer matches this exact change point",
            )
        }
        return id
    }

    private fun replaceParticipants(knowledgeId: UUID, participation: BossParticipation) {
        knowledgeRepository.deleteParticipants(knowledgeId)
        if (participation is BossParticipation.Fixed) {
            participation.bossDefinitionIds.forEach { bossId ->
                knowledgeRepository.insertParticipant(knowledgeId, bossId.value)
            }
        }
    }

    @Transactional(readOnly = true)
    override fun findAcceptedChangePoints(
        acquisitionSourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedBossEncounterChangePoint> {
        val sourceId = acquisitionSourceId.value
        val major = scope.compatibilityVersion.major
        val minor = scope.compatibilityVersion.minor
        val accepted =
            scope.leagueDefinitionId?.let { leagueId ->
                knowledgeRepository.findLeagueSpecificChangePointsWithParticipants(
                    sourceId,
                    major,
                    minor,
                    scope.ruleset,
                    leagueId.value,
                )
            } ?: knowledgeRepository.findLeagueIndependentChangePointsWithParticipants(
                sourceId,
                major,
                minor,
                scope.ruleset,
            )
        return accepted.map { toDomain(it, scope) }
    }

    private fun toDomain(
        entity: BossEncounterKnowledgeEntity,
        scope: AcquisitionKnowledgeScope,
    ): AcceptedBossEncounterChangePoint {
        val participantIds = entity.bossDefinitionIds.mapTo(linkedSetOf()) { BossDefinitionId(it) }
        val participation =
            when (entity.participationKind) {
                BossEncounterParticipationKind.FIXED -> {
                    if (participantIds.isEmpty()) {
                        throw CorruptBossEncounterKnowledgeException(
                            "Persisted FIXED encounter cannot have an empty boss roster",
                        )
                    }
                    BossParticipation.Fixed(participantIds)
                }

                BossEncounterParticipationKind.CONFIGURATION_DEPENDENT -> {
                    if (participantIds.isNotEmpty()) {
                        throw CorruptBossEncounterKnowledgeException(
                            "Persisted CONFIGURATION_DEPENDENT encounter must not have fixed participants",
                        )
                    }
                    BossParticipation.ConfigurationDependent
                }
            }
        return AcceptedBossEncounterChangePoint(
            snapshot =
            BossEncounterKnowledgeSnapshot(
                effectiveFromPatch =
                entity.effectivePatchNumber?.let { patch ->
                    GamePatch(scope.compatibilityVersion, patch, entity.effectivePatchSuffix)
                },
                displayLabel = entity.displayLabel,
                availability = entity.availability,
                bossParticipation = participation,
            ),
            revision = entity.revision,
        )
    }

    private fun kind(participation: BossParticipation): BossEncounterParticipationKind = when (participation) {
        is BossParticipation.Fixed -> BossEncounterParticipationKind.FIXED
        BossParticipation.ConfigurationDependent -> BossEncounterParticipationKind.CONFIGURATION_DEPENDENT
    }

    private fun translateIntegrityViolation(exception: DataIntegrityViolationException): RuntimeException =
        if (exception.hasConstraintName(CHANGE_POINT_UNIQUE_CONSTRAINT)) {
            BossEncounterKnowledgeConflictException(
                "Another accepted encounter fact already exists for this exact change point",
                exception,
            )
        } else {
            exception
        }

    private fun Throwable.hasConstraintName(constraintName: String): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ConstraintViolationException && current.constraintName == constraintName) return true
            current = current.cause
        }
        return false
    }

    private companion object {
        const val CHANGE_POINT_UNIQUE_CONSTRAINT = "uq_boss_encounter_knowledge_change_point"
    }
}
