package com.colinmoerbe.poecompanion.account.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/**
 * Composite JPA identity for one manual ownership quantity.
 */
@Embeddable
internal data class ManualOwnershipEntityId(
    @Column(name = "account_context_id", nullable = false, updatable = false)
    var accountContextId: UUID,
    @Column(name = "unique_definition_id", nullable = false, updatable = false)
    var uniqueDefinitionId: UUID,
) : Serializable {
    private companion object {
        const val serialVersionUID: Long = 1L
    }
}
