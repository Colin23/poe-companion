package com.colinmoerbe.poecompanion.catalog.persistence

import com.colinmoerbe.poecompanion.catalog.ExternalProvider
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.io.Serializable

/**
 * Composite JPA identity for an external provider mapping.
 */
@Embeddable
internal data class ExternalIdentityEntityId(
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, updatable = false, length = 32)
    var provider: ExternalProvider,
    @Column(name = "provider_key", nullable = false, updatable = false, length = 1024)
    var providerKey: String,
) : Serializable
