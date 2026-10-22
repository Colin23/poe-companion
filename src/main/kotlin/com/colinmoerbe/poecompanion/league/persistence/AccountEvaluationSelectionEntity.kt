package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.Ruleset
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** Per-user game environment selection, independent of any owned AccountContext. */
@Entity
@Table(name = "account_evaluation_selection")
internal open class AccountEvaluationSelectionEntity(
    @Id
    @Column(name = "application_user_id", nullable = false, updatable = false)
    open var applicationUserId: UUID,
    @Column(name = "league_definition_id", nullable = false)
    open var leagueDefinitionId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "ruleset", nullable = false, length = 32)
    open var ruleset: Ruleset,
    @Column(name = "compatibility_version_major", nullable = false)
    open var compatibilityVersionMajor: Int,
    @Column(name = "compatibility_version_minor", nullable = false)
    open var compatibilityVersionMinor: Int,
    @Column(name = "game_patch")
    open var gamePatch: Int?,
    @Column(name = "game_patch_suffix", length = 32)
    open var gamePatchSuffix: String?,
)
