package com.colinmoerbe.poecompanion.league.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/** Composite JPA identity for an explicitly curated league/compatibility-version association. */
@Embeddable
internal data class SelectableLeagueVersionEntityId(
    @Column(name = "league_definition_id", nullable = false, updatable = false)
    var leagueDefinitionId: UUID,
    @Column(name = "compatibility_version_major", nullable = false, updatable = false)
    var compatibilityVersionMajor: Int,
    @Column(name = "compatibility_version_minor", nullable = false, updatable = false)
    var compatibilityVersionMinor: Int,
) : Serializable {
    private companion object {
        const val serialVersionUID: Long = 1L
    }
}
