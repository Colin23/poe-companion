package com.colinmoerbe.poecompanion.league

import java.util.UUID

/**
 * Stable application-owned identity of one isolated personalized PoE account context.
 */
data class AccountContextId(val value: UUID) {
    companion object {
        /** Creates a new application-owned account context identity. */
        fun generate(): AccountContextId = AccountContextId(UUID.randomUUID())
    }
}
