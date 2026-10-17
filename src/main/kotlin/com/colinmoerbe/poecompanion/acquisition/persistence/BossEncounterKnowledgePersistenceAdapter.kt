package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.AcceptedBossEncounterChangePoint
import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeConflictException
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgePersistencePort
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.BossParticipation
import com.colinmoerbe.poecompanion.league.GamePatch
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Keeps an accepted snapshot and its entire fixed boss roster in one transaction.
 *
 * Exact-scope reconstruction uses one outer-joined SQL statement so that PostgreSQL provides one MVCC
 * statement snapshot for the parent and child records, even under READ COMMITTED.
 */
@Repository
internal class BossEncounterKnowledgePersistenceAdapter(
    private val knowledgeRepository: SpringDataBossEncounterKnowledgeRepository,
    private val sourceRepository: SpringDataAcquisitionSourceRepository,
    private val bossRepository: SpringDataBossDefinitionRepository,
    private val jdbc: JdbcTemplate,
    private val namedJdbc: NamedParameterJdbcTemplate,
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
        val parameters = scopeParameters(acquisitionSourceId, scope, snapshot.effectiveFromPatch)
            .addValue("label", snapshot.displayLabel)
            .addValue("availability", snapshot.availability.name)
            .addValue("participationKind", kind(snapshot.bossParticipation).name)

        if (expectedRevision != null) {
            val updated =
                knowledgeRepository.replaceIfCurrent(
                    sourceId = acquisitionSourceId.value,
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
            val knowledgeId =
                requireNotNull(namedJdbc.queryForObject(FIND_ACCEPTED_ID, parameters, UUID::class.java)) {
                    "Corrected encounter change point disappeared inside its transaction"
                }
            replaceParticipants(knowledgeId, snapshot.bossParticipation)
            return expectedRevision + 1
        }

        val knowledgeId = UUID.randomUUID()
        try {
            knowledgeRepository.insertAccepted(
                id = knowledgeId,
                sourceId = acquisitionSourceId.value,
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
        replaceParticipants(knowledgeId, snapshot.bossParticipation)
        return 0
    }

    private fun replaceParticipants(knowledgeId: UUID, participation: BossParticipation) {
        jdbc.update("DELETE FROM poe_companion.boss_encounter_participant WHERE knowledge_id = ?", knowledgeId)
        if (participation is BossParticipation.Fixed) {
            participation.bossDefinitionIds.forEach { bossId ->
                jdbc.update(
                    """
                    INSERT INTO poe_companion.boss_encounter_participant (knowledge_id, boss_definition_id)
                    VALUES (?, ?)
                    """.trimIndent(),
                    knowledgeId,
                    bossId.value,
                )
            }
        }
    }

    @Transactional(readOnly = true)
    override fun findAcceptedChangePoints(
        acquisitionSourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedBossEncounterChangePoint> {
        val parameters = scopeParameters(acquisitionSourceId, scope, null)
        return namedJdbc.query(
            FIND_ACCEPTED_CHANGE_POINTS,
            parameters,
            ResultSetExtractor { rows ->
                val records = linkedMapOf<UUID, EncounterRow>()
                while (rows.next()) {
                    val id = rows.getObject("id", UUID::class.java)
                    val record =
                        records.getOrPut(id) {
                            EncounterRow(
                                patchNumber = rows.getObject("effective_patch_number") as Int?,
                                patchSuffix = rows.getString("effective_patch_suffix"),
                                displayLabel = rows.getString("display_label"),
                                availability = AcquisitionSourceAvailability.valueOf(rows.getString("availability")),
                                participationKind = BossEncounterParticipationKind.valueOf(
                                    rows.getString("participation_kind"),
                                ),
                                revision = rows.getLong("revision"),
                            )
                        }
                    rows.getObject("boss_definition_id", UUID::class.java)?.let {
                        record.bossIds.add(BossDefinitionId(it))
                    }
                }
                records.values.map { toDomain(it, scope) }
            },
        )
    }

    private fun toDomain(row: EncounterRow, scope: AcquisitionKnowledgeScope): AcceptedBossEncounterChangePoint {
        val participation =
            when (row.participationKind) {
                BossEncounterParticipationKind.FIXED -> {
                    check(row.bossIds.isNotEmpty()) { "Persisted FIXED encounter cannot have an empty boss roster" }
                    BossParticipation.Fixed(row.bossIds)
                }

                BossEncounterParticipationKind.CONFIGURATION_DEPENDENT -> {
                    check(row.bossIds.isEmpty()) {
                        "Persisted CONFIGURATION_DEPENDENT encounter must not have fixed participants"
                    }
                    BossParticipation.ConfigurationDependent
                }
            }
        return AcceptedBossEncounterChangePoint(
            snapshot =
            BossEncounterKnowledgeSnapshot(
                effectiveFromPatch =
                row.patchNumber?.let { GamePatch(scope.compatibilityVersion, it, row.patchSuffix) },
                displayLabel = row.displayLabel,
                availability = row.availability,
                bossParticipation = participation,
            ),
            revision = row.revision,
        )
    }

    private fun kind(participation: BossParticipation): BossEncounterParticipationKind = when (participation) {
        is BossParticipation.Fixed -> BossEncounterParticipationKind.FIXED
        BossParticipation.ConfigurationDependent -> BossEncounterParticipationKind.CONFIGURATION_DEPENDENT
    }

    private fun scopeParameters(
        sourceId: AcquisitionSourceId,
        scope: AcquisitionKnowledgeScope,
        patch: GamePatch?,
    ): MapSqlParameterSource = MapSqlParameterSource()
        .addValue("sourceId", sourceId.value)
        .addValue("major", scope.compatibilityVersion.major)
        .addValue("minor", scope.compatibilityVersion.minor)
        .addValue("ruleset", scope.ruleset.name)
        .addValue("leagueId", scope.leagueDefinitionId?.value)
        .addValue("patchNumber", patch?.patch)
        .addValue("patchSuffix", patch?.suffix)

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

    private data class EncounterRow(
        val patchNumber: Int?,
        val patchSuffix: String?,
        val displayLabel: String,
        val availability: AcquisitionSourceAvailability,
        val participationKind: BossEncounterParticipationKind,
        val revision: Long,
        val bossIds: MutableSet<BossDefinitionId> = linkedSetOf(),
    )

    private companion object {
        const val CHANGE_POINT_UNIQUE_CONSTRAINT = "uq_boss_encounter_knowledge_change_point"

        const val FIND_ACCEPTED_ID = """
            SELECT id FROM poe_companion.boss_encounter_knowledge
             WHERE acquisition_source_id = :sourceId
               AND compatibility_version_major = :major
               AND compatibility_version_minor = :minor
               AND ruleset = :ruleset
               AND league_definition_id IS NOT DISTINCT FROM :leagueId
               AND effective_patch_number IS NOT DISTINCT FROM :patchNumber
               AND effective_patch_suffix IS NOT DISTINCT FROM :patchSuffix
        """

        const val FIND_ACCEPTED_CHANGE_POINTS = """
            SELECT k.id, k.effective_patch_number, k.effective_patch_suffix, k.display_label,
                   k.availability, k.participation_kind, k.revision, p.boss_definition_id
              FROM poe_companion.boss_encounter_knowledge k
              LEFT JOIN poe_companion.boss_encounter_participant p ON p.knowledge_id = k.id
             WHERE k.acquisition_source_id = :sourceId
               AND k.compatibility_version_major = :major
               AND k.compatibility_version_minor = :minor
               AND k.ruleset = :ruleset
               AND k.league_definition_id IS NOT DISTINCT FROM :leagueId
             ORDER BY k.id, p.boss_definition_id
        """
    }
}
