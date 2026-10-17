package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.AcceptedBossEncounterChangePoint
import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeConflictException
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgePersistencePort
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.CorruptBossEncounterKnowledgeException
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
        val sourceId = acquisitionSourceId.value
        val major = scope.compatibilityVersion.major
        val minor = scope.compatibilityVersion.minor
        val ruleset = scope.ruleset.name
        val leagueId = scope.leagueDefinitionId?.value
        val patchNumber = snapshot.effectiveFromPatch?.patch
        val patchSuffix = snapshot.effectiveFromPatch?.suffix
        val participationKind = kind(snapshot.bossParticipation).name

        val knowledgeId =
            if (expectedRevision == null) {
                val id = UUID.randomUUID()
                try {
                    knowledgeRepository.insertAccepted(
                        id = id,
                        sourceId = sourceId,
                        major = major,
                        minor = minor,
                        ruleset = ruleset,
                        leagueId = leagueId,
                        patchNumber = patchNumber,
                        patchSuffix = patchSuffix,
                        label = snapshot.displayLabel,
                        availability = snapshot.availability.name,
                        participationKind = participationKind,
                    )
                } catch (exception: DataIntegrityViolationException) {
                    throw translateIntegrityViolation(exception)
                }
                id
            } else {
                val id = knowledgeRepository.findAcceptedId(
                    sourceId = sourceId,
                    major = major,
                    minor = minor,
                    ruleset = ruleset,
                    leagueId = leagueId,
                    patchNumber = patchNumber,
                    patchSuffix = patchSuffix,
                ) ?: throw BossEncounterKnowledgeConflictException(
                    "No accepted encounter knowledge exists at this exact change point",
                )

                val updated =
                    knowledgeRepository.replaceIfCurrent(
                        sourceId = sourceId,
                        major = major,
                        minor = minor,
                        ruleset = ruleset,
                        leagueId = leagueId,
                        patchNumber = patchNumber,
                        patchSuffix = patchSuffix,
                        label = snapshot.displayLabel,
                        availability = snapshot.availability.name,
                        participationKind = participationKind,
                        expectedRevision = expectedRevision,
                    )
                if (updated != 1) {
                    throw BossEncounterKnowledgeConflictException(
                        "Expected encounter knowledge revision no longer matches this exact change point",
                    )
                }
                id
            }

        replaceParticipants(knowledgeId, snapshot.bossParticipation)
        return expectedRevision?.plus(1) ?: 0
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
                    sourceId, major, minor, scope.ruleset, leagueId.value,
                )
            } ?: knowledgeRepository.findLeagueIndependentChangePointsWithParticipants(
                sourceId, major, minor, scope.ruleset,
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
                entity.effectivePatchNumber?.let { GamePatch(scope.compatibilityVersion, it, entity.effectivePatchSuffix) },
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
