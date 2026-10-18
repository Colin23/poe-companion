package com.colinmoerbe.poecompanion.acquisition.relationship.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA identity for a canonical source-to-Unique association.
 *
 * The identity and its defining pair are immutable; versioned CAN_DROP/CANNOT_DROP knowledge is stored separately.
 */
@Entity
@Table(name = "drop_relationship")
internal open class DropRelationshipEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
    @Column(name = "acquisition_source_id", nullable = false, updatable = false)
    open var acquisitionSourceId: UUID,
    @Column(name = "unique_definition_id", nullable = false, updatable = false)
    open var uniqueDefinitionId: UUID,
)
