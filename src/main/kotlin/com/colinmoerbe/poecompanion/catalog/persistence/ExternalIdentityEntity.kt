package com.colinmoerbe.poecompanion.catalog.persistence

import com.colinmoerbe.poecompanion.catalog.ExternalProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.util.UUID

/**
 * JPA representation of a trusted external identity mapping.
 */
@Entity
@Table(name = "external_identity")
@IdClass(ExternalIdentityEntityId::class)
internal open class ExternalIdentityEntity(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 32)
    open var provider: ExternalProvider,
    @Id
    @Column(name = "provider_key", nullable = false, updatable = false, length = 1024)
    open var providerKey: String,
    @Column(name = "unique_definition_id", nullable = false)
    open var uniqueDefinitionId: UUID,
)
