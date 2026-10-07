package com.colinmoerbe.poecompanion.build.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

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

/**
 * JPA representation of one ordered Unique requirement inside a requirement group snapshot.
 */
@Entity
@Table(name = "build_requirement")
internal open class RequirementEntity(
    @EmbeddedId
    open var id: RequirementEntityId,
    @Column(name = "unique_definition_id", nullable = false, updatable = false)
    open var uniqueDefinitionId: UUID,
    @Column(name = "required_quantity", nullable = false)
    open var requiredQuantity: Int,
)
