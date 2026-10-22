package com.colinmoerbe.poecompanion.league.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

/** Composite identity for an explicitly curated league/compatibility-version association. */
internal data class SelectableLeagueVersionKey(
    var leagueDefinitionId: UUID = UUID(0L, 0L),
    var compatibilityVersionMajor: Int = 0,
    var compatibilityVersionMinor: Int = 0,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** A supported selector option; it does not express whether a Challenge League is active. */
@Entity
@Table(name = "selectable_league_version")
@IdClass(SelectableLeagueVersionKey::class)
internal open class SelectableLeagueVersionEntity(
    @Id
    @Column(name = "league_definition_id", nullable = false, updatable = false)
    open var leagueDefinitionId: UUID,
    @Id
    @Column(name = "compatibility_version_major", nullable = false, updatable = false)
    open var compatibilityVersionMajor: Int,
    @Id
    @Column(name = "compatibility_version_minor", nullable = false, updatable = false)
    open var compatibilityVersionMinor: Int,
)
