package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.league.Ruleset
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.util.UUID

/**
 * One accepted complete encounter fact at an exact game change point.
 *
 * The boss roster is a separate relational set; no cross-module JPA object graph is exposed.
 */
@Entity
@Table(name = "boss_encounter_knowledge")
internal open class BossEncounterKnowledgeEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "acquisition_source_id", nullable = false, updatable = false)
    open var acquisitionSourceId: UUID,
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
    @Column(name = "display_label", nullable = false, columnDefinition = "TEXT")
    open var displayLabel: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 32)
    open var availability: AcquisitionSourceAvailability,
    @Enumerated(EnumType.STRING)
    @Column(name = "participation_kind", nullable = false, length = 32)
    open var participationKind: BossEncounterParticipationKind,
    @Column(name = "revision", nullable = false)
    open var revision: Long,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "boss_encounter_participant", joinColumns = [JoinColumn(name = "knowledge_id")])
    @Column(name = "boss_definition_id", nullable = false)
    open var bossDefinitionIds: MutableSet<UUID> = linkedSetOf(),
)
