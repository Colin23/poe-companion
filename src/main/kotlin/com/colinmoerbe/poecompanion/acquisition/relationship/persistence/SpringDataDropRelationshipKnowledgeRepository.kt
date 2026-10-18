package com.colinmoerbe.poecompanion.acquisition.relationship.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataDropRelationshipKnowledgeRepository :
    JpaRepository<DropRelationshipKnowledgeEntity, UUID> {
    @Modifying
    @Query(
        value =
        """
            INSERT INTO poe_companion.drop_relationship_knowledge (
                id, drop_relationship_id, compatibility_version_major, compatibility_version_minor,
                ruleset, league_definition_id, effective_patch_number, effective_patch_suffix,
                state, revision
            ) VALUES (
                :id, :relationshipId, :major, :minor,
                :ruleset, :leagueId, :patchNumber, :patchSuffix, :state, 0
            )
            """,
        nativeQuery = true,
    )
    fun insertAccepted(
        @Param("id") id: UUID,
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
        @Param("state") state: String,
    ): Int

    @Modifying
    @Query(
        value =
        """
            UPDATE poe_companion.drop_relationship_knowledge
            SET state = :state, revision = revision + 1
            WHERE drop_relationship_id = :relationshipId
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
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
        @Param("state") state: String,
        @Param("expectedRevision") expectedRevision: Long,
    ): Int

    @Query(
        value =
        """
            SELECT * FROM poe_companion.drop_relationship_knowledge
            WHERE drop_relationship_id = :relationshipId
              AND compatibility_version_major = :major
              AND compatibility_version_minor = :minor
              AND ruleset = :ruleset
              AND league_definition_id IS NOT DISTINCT FROM :leagueId
            """,
        nativeQuery = true,
    )
    fun findAcceptedByExactScope(
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
    ): List<DropRelationshipKnowledgeEntity>
}
