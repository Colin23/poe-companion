package com.colinmoerbe.poecompanion.build.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of a build variant.
 */
@Entity
@Table(name = "build_variant")
internal open class BuildVariantEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "build_archetype_id", nullable = false, updatable = false)
    open var buildArchetypeId: UUID,
    @Column(name = "label", length = 255)
    open var label: String?,
)
