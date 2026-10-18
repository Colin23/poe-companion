package com.colinmoerbe.poecompanion.acquisition.attempts.persistence

import java.io.Serializable
import java.util.UUID
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * Composite JPA identity for one player's recorded attempt count at a canonical source.
 */
@Embeddable
internal data class AvailableAttemptsEntityId(
    @Column(name = "account_context_id", nullable = false, updatable = false)
    var accountContextId: UUID,
    @Column(name = "acquisition_source_id", nullable = false, updatable = false)
    var acquisitionSourceId: UUID,
) : Serializable {
    private companion object {
        const val serialVersionUID: Long = 1L
    }
}
