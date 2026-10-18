package com.colinmoerbe.poecompanion.acquisition.attempts.persistence

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * JPA representation of a manually recorded available-attempt count, including explicit zero.
 */
@Entity
@Table(name = "available_attempts")
internal open class AvailableAttemptsEntity(
    @EmbeddedId
    open var id: AvailableAttemptsEntityId,
    @Column(name = "attempt_count", nullable = false)
    open var attemptCount: Int,
)
