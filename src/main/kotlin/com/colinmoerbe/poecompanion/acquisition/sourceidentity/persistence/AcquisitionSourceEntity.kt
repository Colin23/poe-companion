package com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence

import java.util.UUID
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

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
