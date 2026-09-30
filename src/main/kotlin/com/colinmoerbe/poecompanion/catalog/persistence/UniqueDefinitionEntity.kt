package com.colinmoerbe.poecompanion.catalog.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of a catalog unique definition.
 *
 * Persistence concerns remain separate from the catalog domain model so application code can use typed domain IDs
 * without depending on JPA.
 */
@Entity
@Table(name = "unique_definition")
internal open class UniqueDefinitionEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
)
