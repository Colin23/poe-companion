package com.colinmoerbe.poecompanion.acquisition.relationship.persistence

import com.colinmoerbe.poecompanion.acquisition.DropRelationshipState
import com.colinmoerbe.poecompanion.league.Ruleset
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One mutable accepted change point. Only the authored state and edit revision change after insertion.
 */
@Entity
@Table(name = "drop_relationship_knowledge")
internal open class DropRelationshipKnowledgeEntity(
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
    @Column(name = "state", nullable = false, length = 32)
    open var state: DropRelationshipState,
    @Column(name = "revision", nullable = false)
    open var revision: Long,
)
