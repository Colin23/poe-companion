package com.colinmoerbe.poecompanion.build.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of a build archetype.
 */
@Entity
@Table(name = "build_archetype")
internal open class BuildArchetypeEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "name", nullable = false, length = 255)
    open var name: String,
)
