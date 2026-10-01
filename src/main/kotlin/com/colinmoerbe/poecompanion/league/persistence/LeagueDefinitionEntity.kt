package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of a PoE league definition.
 */
@Entity
@Table(name = "league_definition")
internal open class LeagueDefinitionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "name", nullable = false, length = 255)
    open var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "league_type", nullable = false, length = 32)
    open var type: LeagueType,
    @Enumerated(EnumType.STRING)
    @Column(name = "participation", nullable = false, length = 32)
    open var participation: LeagueParticipation,
    @Enumerated(EnumType.STRING)
    @Column(name = "mortality", nullable = false, length = 32)
    open var mortality: LeagueMortality,
    @Enumerated(EnumType.STRING)
    @Column(name = "realm", nullable = false, length = 32)
    open var realm: GameRealm,
)
