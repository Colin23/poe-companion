package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.DropEstimateProvenance
import com.colinmoerbe.poecompanion.league.Ruleset
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One immutable estimate or withdrawal with a separate mutable ACTIVE/SUPERSEDED publication lifecycle.
 */
@Entity
@Table(name = "fixed_drop_estimate")
internal open class FixedDropEstimateEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "drop_relationship_id", nullable = false, updatable = false)
    open var dropRelationshipId: UUID,
    @Column(name = "compatibility_version_major", nullable = false, updatable = false)
    open var compatibilityVersionMajor: Int,
    @Column(name = "compatibility_version_minor", nullable = false, updatable = false)
    open var compatibilityVersionMinor: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "ruleset", nullable = false, updatable = false, length = 32)
    open var ruleset: Ruleset,
    @Column(name = "league_definition_id", updatable = false)
    open var leagueDefinitionId: UUID?,
    @Column(name = "model_type", nullable = false, updatable = false, length = 32)
    open var modelType: String,
    @Column(name = "effective_patch_number", updatable = false)
    open var effectivePatchNumber: Int?,
    @Column(name = "effective_patch_suffix", updatable = false, length = 32)
    open var effectivePatchSuffix: String?,
    @Column(name = "probability_per_attempt", updatable = false)
    open var probabilityPerAttempt: Double?,
    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", updatable = false, length = 32)
    open var provenance: DropEstimateProvenance?,
    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_kind", nullable = false, updatable = false, length = 16)
    open var knowledgeKind: FixedProbabilityKnowledgeKind,
    @Column(name = "withdrawal_reason", updatable = false, columnDefinition = "TEXT")
    open var withdrawalReason: String?,
    @Column(name = "evidence_reference", nullable = false, updatable = false, columnDefinition = "TEXT")
    open var evidenceReference: String,
    @Column(name = "reviewed_at", nullable = false, updatable = false)
    open var reviewedAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    open var status: FixedDropEstimateStatus,
    @Column(name = "published_at", nullable = false, updatable = false)
    open var publishedAt: Instant,
    @Column(name = "superseded_at")
    open var supersededAt: Instant?,
)
