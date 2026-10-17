package com.colinmoerbe.poecompanion.acquisition.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataBossEncounterKnowledgeRepository : JpaRepository<BossEncounterKnowledgeEntity, UUID> {
    @Modifying
    @Query(
        value =
        """
            INSERT INTO poe_companion.boss_encounter_knowledge (
                id, acquisition_source_id, compatibility_version_major, compatibility_version_minor,
                ruleset, league_definition_id, effective_patch_number, effective_patch_suffix,
                display_label, availability, participation_kind, revision
            ) VALUES (
                :id, :sourceId, :major, :minor, :ruleset, :leagueId, :patchNumber, :patchSuffix,
                :label, :availability, :participationKind, 0
            )
            """,
        nativeQuery = true,
    )
    fun insertAccepted(
        @Param("id") id: UUID,
        @Param("sourceId") sourceId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
        @Param("label") label: String,
        @Param("availability") availability: String,
        @Param("participationKind") participationKind: String,
    ): Int

    @Modifying
    @Query(
        value =
        """
            UPDATE poe_companion.boss_encounter_knowledge
               SET display_label = :label,
                   availability = :availability,
                   participation_kind = :participationKind,
                   revision = revision + 1
             WHERE acquisition_source_id = :sourceId
               AND compatibility_version_major = :major
               AND compatibility_version_minor = :minor
               AND ruleset = :ruleset
               AND league_definition_id IS NOT DISTINCT FROM :leagueId
               AND effective_patch_number IS NOT DISTINCT FROM :patchNumber
               AND effective_patch_suffix IS NOT DISTINCT FROM :patchSuffix
               AND revision = :expectedRevision
            """,
        nativeQuery = true,
    )
    fun replaceIfCurrent(
        @Param("sourceId") sourceId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
        @Param("label") label: String,
        @Param("availability") availability: String,
        @Param("participationKind") participationKind: String,
        @Param("expectedRevision") expectedRevision: Long,
    ): Int
}
