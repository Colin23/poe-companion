package com.colinmoerbe.poecompanion.build.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/**
 * Composite JPA identity for one ordered requirement in a build revision snapshot.
 */
@Embeddable
internal data class RequirementEntityId(
    @Column(name = "build_variant_revision_id", nullable = false, updatable = false)
    var buildVariantRevisionId: UUID,
    @Column(name = "group_position", nullable = false, updatable = false)
    var groupPosition: Int,
    @Column(name = "position", nullable = false, updatable = false)
    var position: Int,
) : Serializable {
    private companion object {
        const val serialVersionUID: Long = 1L
    }
}
