package com.colinmoerbe.poecompanion.acquisition.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of a canonical acquisition source's stable identity.
 *
 * Versioned encounter knowledge and source availability are modeled separately.
 */
@Entity
@Table(name = "acquisition_source")
internal open class AcquisitionSourceEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    open var id: UUID,
)
