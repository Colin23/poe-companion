package com.colinmoerbe.poecompanion.acquisition.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Stable boss identity and display metadata. A rename needs a deliberate future command, not an accidental upsert.
 */
@Entity
@Table(name = "boss_definition")
internal open class BossDefinitionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "display_name", nullable = false, updatable = false, columnDefinition = "TEXT")
    open var displayName: String,
)
