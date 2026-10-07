package com.colinmoerbe.poecompanion.build.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/**
 * Composite JPA identity for one ordered requirement group in a build revision snapshot.
 */
@Embeddable
internal data class RequirementGroupEntityId(
    @Column(name = "build_variant_revision_id", nullable = false, updatable = false)
    var buildVariantRevisionId: UUID,
    @Column(name = "position", nullable = false, updatable = false)
    var position: Int,
) : Serializable {
    private companion object {
        const val serialVersionUID: Long = 1L
    }
}
