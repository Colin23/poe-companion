package com.colinmoerbe.poecompanion.acquisition.behavior.persistence

import com.colinmoerbe.poecompanion.acquisition.behavior.DropProbabilityBehavior
import com.colinmoerbe.poecompanion.league.Ruleset
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** One accepted mutable curator fact. Identity and context are immutable; behavior and revision may change. */
@Entity
@Table(name = "drop_probability_behavior_knowledge")
internal open class DropProbabilityBehaviorKnowledgeEntity(
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
    @Column(name = "effective_patch_number", updatable = false)
    open var effectivePatchNumber: Int?,
    @Column(name = "effective_patch_suffix", updatable = false, length = 32)
    open var effectivePatchSuffix: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "behavior", nullable = false, length = 32)
    open var behavior: DropProbabilityBehavior,
    @Column(name = "revision", nullable = false)
    open var revision: Long,
)
