package com.colinmoerbe.poecompanion.build.persistence

import com.colinmoerbe.poecompanion.build.BuildVariantRevisionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of one build variant revision.
 */
@Entity
@Table(name = "build_variant_revision")
internal open class BuildVariantRevisionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "build_variant_id", nullable = false, updatable = false)
    open var buildVariantId: UUID,
    @Column(name = "compatibility_version_major", nullable = false, updatable = false)
    open var compatibilityVersionMajor: Int,
    @Column(name = "compatibility_version_minor", nullable = false, updatable = false)
    open var compatibilityVersionMinor: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    open var status: BuildVariantRevisionStatus,
)
