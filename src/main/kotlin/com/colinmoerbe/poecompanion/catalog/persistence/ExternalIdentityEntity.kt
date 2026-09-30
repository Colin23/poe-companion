package com.colinmoerbe.poecompanion.catalog.persistence

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of a trusted external identity mapping.
 */
@Entity
@Table(name = "external_identity")
internal open class ExternalIdentityEntity(
    @EmbeddedId
    open var id: ExternalIdentityEntityId,
    @Column(name = "unique_definition_id", nullable = false)
    open var uniqueDefinitionId: UUID,
)
