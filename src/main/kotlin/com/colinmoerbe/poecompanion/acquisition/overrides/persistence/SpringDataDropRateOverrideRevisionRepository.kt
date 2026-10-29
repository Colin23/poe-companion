package com.colinmoerbe.poecompanion.acquisition.overrides.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Exact-key reads and compare-and-swap writes. PostgreSQL enforces active-slot uniqueness under concurrency.
 */
internal interface SpringDataDropRateOverrideRevisionRepository : JpaRepository<DropRateOverrideRevisionEntity, UUID> {
    @Query(
        value = """
            SELECT * FROM poe_companion.drop_rate_override_revision
            WHERE account_context_id = :accountId
              AND drop_relationship_id = :relationshipId
              AND compatibility_version_major = :major
              AND compatibility_version_minor = :minor
              AND game_patch_number IS NOT DISTINCT FROM :patchNumber
              AND game_patch_suffix IS NOT DISTINCT FROM :patchSuffix
              AND model_type = 'FIXED_BERNOULLI'
              AND status = 'ACTIVE'
            """,
        nativeQuery = true,
    )
    fun findActiveByExactKey(
        @Param("accountId") accountId: UUID,
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
    ): DropRateOverrideRevisionEntity?

    @Modifying
    @Query(
        value = """
            INSERT INTO poe_companion.drop_rate_override_revision (
                id, account_context_id, drop_relationship_id,
                compatibility_version_major, compatibility_version_minor,
                game_patch_number, game_patch_suffix, model_type,
                probability_per_attempt, status, confirmed_at
            ) VALUES (
                :revisionId, :accountId, :relationshipId,
                :major, :minor,
                :patchNumber, :patchSuffix, 'FIXED_BERNOULLI',
                :probability, 'ACTIVE', CURRENT_TIMESTAMP
            )
            """,
        nativeQuery = true,
    )
    fun insertActive(
        @Param("revisionId") revisionId: UUID,
        @Param("accountId") accountId: UUID,
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
        @Param("probability") probability: Double,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE poe_companion.drop_rate_override_revision
            SET status = :nextStatus, ended_at = CURRENT_TIMESTAMP
            WHERE id = :expectedRevisionId
              AND status = 'ACTIVE'
              AND account_context_id = :accountId
              AND drop_relationship_id = :relationshipId
              AND compatibility_version_major = :major
              AND compatibility_version_minor = :minor
              AND game_patch_number IS NOT DISTINCT FROM :patchNumber
              AND game_patch_suffix IS NOT DISTINCT FROM :patchSuffix
              AND model_type = 'FIXED_BERNOULLI'
            """,
        nativeQuery = true,
    )
    fun endActiveIfExpected(
        @Param("expectedRevisionId") expectedRevisionId: UUID,
        @Param("nextStatus") nextStatus: String,
        @Param("accountId") accountId: UUID,
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
    ): Int
}
