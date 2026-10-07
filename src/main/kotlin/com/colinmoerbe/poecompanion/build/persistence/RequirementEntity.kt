package com.colinmoerbe.poecompanion.build.persistence

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

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
