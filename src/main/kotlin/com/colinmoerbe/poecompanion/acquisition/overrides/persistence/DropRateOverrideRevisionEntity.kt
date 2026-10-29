package com.colinmoerbe.poecompanion.acquisition.overrides.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Immutable personal probability revision with mutable persistence-only lifecycle fields.
 *
 * League identity and ruleset are supplied by the immutable AccountContext, not duplicated here.
 */
@Entity
@Table(name = "drop_rate_override_revision")
internal open class DropRateOverrideRevisionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "account_context_id", nullable = false, updatable = false)
    open var accountContextId: UUID,
    @Column(name = "drop_relationship_id", nullable = false, updatable = false)
    open var dropRelationshipId: UUID,
    @Column(name = "compatibility_version_major", nullable = false, updatable = false)
    open var compatibilityVersionMajor: Int,
    @Column(name = "compatibility_version_minor", nullable = false, updatable = false)
    open var compatibilityVersionMinor: Int,
    @Column(name = "game_patch_number", updatable = false)
    open var gamePatchNumber: Int?,
    @Column(name = "game_patch_suffix", updatable = false, length = 32)
    open var gamePatchSuffix: String?,
    @Column(name = "model_type", nullable = false, updatable = false, length = 32)
    open var modelType: String,
    @Column(name = "probability_per_attempt", nullable = false, updatable = false)
    open var probabilityPerAttempt: Double,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    open var status: DropRateOverrideRevisionStatus,
    @Column(name = "confirmed_at", nullable = false, updatable = false)
    open var confirmedAt: Instant,
    @Column(name = "ended_at")
    open var endedAt: Instant?,
)
