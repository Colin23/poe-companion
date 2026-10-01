package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.Ruleset
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of one isolated account context.
 */
@Entity
@Table(name = "account_context")
internal open class AccountContextEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "league_definition_id", nullable = false, updatable = false)
    open var leagueDefinitionId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "ruleset", nullable = false, updatable = false, length = 32)
    open var ruleset: Ruleset,
)
