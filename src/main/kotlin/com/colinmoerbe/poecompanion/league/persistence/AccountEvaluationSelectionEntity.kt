package com.colinmoerbe.poecompanion.league.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of the application's single current account/evaluation selection.
 */
@Entity
@Table(name = "account_evaluation_selection")
internal open class AccountEvaluationSelectionEntity(
    @Id
    @Column(name = "singleton_id", nullable = false, updatable = false)
    open var singletonId: Int,
    @Column(name = "account_context_id", nullable = false)
    open var accountContextId: UUID,
    @Column(name = "compatibility_version_major", nullable = false)
    open var compatibilityVersionMajor: Int,
    @Column(name = "compatibility_version_minor", nullable = false)
    open var compatibilityVersionMinor: Int,
    @Column(name = "game_patch")
    open var gamePatch: Int?,
    @Column(name = "game_patch_suffix", length = 32)
    open var gamePatchSuffix: String?,
)
