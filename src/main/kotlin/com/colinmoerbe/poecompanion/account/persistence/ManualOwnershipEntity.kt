package com.colinmoerbe.poecompanion.account.persistence

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * JPA representation of one manually maintained ownership quantity.
 */
@Entity
@Table(name = "manual_ownership")
internal open class ManualOwnershipEntity(
    @EmbeddedId
    open var id: ManualOwnershipEntityId,
    @Column(name = "quantity", nullable = false)
    open var quantity: Int,
)
