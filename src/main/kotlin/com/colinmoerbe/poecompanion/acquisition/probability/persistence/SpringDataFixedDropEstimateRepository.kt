package com.colinmoerbe.poecompanion.acquisition.probability.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

internal interface SpringDataFixedDropEstimateRepository : JpaRepository<FixedDropEstimateEntity, UUID> {
    @Modifying
    @Query(
        value =
        """
            INSERT INTO poe_companion.fixed_drop_estimate (
                id, drop_relationship_id, compatibility_version_major, compatibility_version_minor,
                ruleset, league_definition_id, model_type, effective_patch_number, effective_patch_suffix,
                probability_per_attempt, provenance, evidence_reference, reviewed_at, status, published_at, knowledge_kind
            ) VALUES (
                :id, :relationshipId, :major, :minor,
                :ruleset, :leagueId, 'FIXED_BERNOULLI', :patchNumber, :patchSuffix,
                :probability, :provenance, :evidenceReference, :reviewedAt, 'ACTIVE', CURRENT_TIMESTAMP, 'ESTIMATE'
            )
            """,
        nativeQuery = true,
    )
    fun insertActive(
        @Param("id") id: UUID,
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
        @Param("probability") probability: Double,
        @Param("provenance") provenance: String,
        @Param("evidenceReference") evidenceReference: String,
        @Param("reviewedAt") reviewedAt: Instant,
    ): Int

    @Modifying
    @Query(
        value =
        """
            INSERT INTO poe_companion.fixed_drop_estimate (
                id, drop_relationship_id, compatibility_version_major, compatibility_version_minor,
                ruleset, league_definition_id, model_type, effective_patch_number, effective_patch_suffix,
                evidence_reference, reviewed_at, status, published_at, knowledge_kind, withdrawal_reason
            ) VALUES (
                :id, :relationshipId, :major, :minor,
                :ruleset, :leagueId, 'FIXED_BERNOULLI', :patchNumber, :patchSuffix,
                :evidenceReference, :reviewedAt, 'ACTIVE', CURRENT_TIMESTAMP, 'WITHDRAWAL', :reason
            )
            """,
        nativeQuery = true,
    )
    fun insertActiveWithdrawal(
        @Param("id") id: UUID,
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
        @Param("reason") reason: String,
        @Param("evidenceReference") evidenceReference: String,
        @Param("reviewedAt") reviewedAt: Instant,
    ): Int

    @Modifying
    @Query(
        value =
        """
            UPDATE poe_companion.fixed_drop_estimate
            SET status = 'SUPERSEDED', superseded_at = CURRENT_TIMESTAMP
            WHERE id = :expectedId AND status = 'ACTIVE'
              AND knowledge_kind = :expectedKnowledgeKind
              AND drop_relationship_id = :relationshipId
              AND compatibility_version_major = :major
              AND compatibility_version_minor = :minor
              AND ruleset = :ruleset
              AND league_definition_id IS NOT DISTINCT FROM :leagueId
              AND model_type = 'FIXED_BERNOULLI'
              AND effective_patch_number IS NOT DISTINCT FROM :patchNumber
              AND effective_patch_suffix IS NOT DISTINCT FROM :patchSuffix
            """,
        nativeQuery = true,
    )
    fun supersedeActiveIfExpected(
        @Param("expectedId") expectedId: UUID,
        @Param("expectedKnowledgeKind") expectedKnowledgeKind: String,
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
        @Param("patchNumber") patchNumber: Int?,
        @Param("patchSuffix") patchSuffix: String?,
    ): Int

    @Query(
        value =
        """
            SELECT * FROM poe_companion.fixed_drop_estimate
            WHERE drop_relationship_id = :relationshipId
              AND compatibility_version_major = :major
              AND compatibility_version_minor = :minor
              AND ruleset = :ruleset
              AND league_definition_id IS NOT DISTINCT FROM :leagueId
              AND model_type = 'FIXED_BERNOULLI'
              AND status = 'ACTIVE'
            """,
        nativeQuery = true,
    )
    fun findActiveByExactScope(
        @Param("relationshipId") relationshipId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
        @Param("ruleset") ruleset: String,
        @Param("leagueId") leagueId: UUID?,
    ): List<FixedDropEstimateEntity>
}
