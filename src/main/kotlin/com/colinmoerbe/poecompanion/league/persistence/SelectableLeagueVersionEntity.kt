package com.colinmoerbe.poecompanion.league.persistence

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** A supported selector option; it does not express whether a Challenge League is active. */
@Entity
@Table(name = "selectable_league_version")
internal open class SelectableLeagueVersionEntity(
    @EmbeddedId
    open var id: SelectableLeagueVersionEntityId,
)
