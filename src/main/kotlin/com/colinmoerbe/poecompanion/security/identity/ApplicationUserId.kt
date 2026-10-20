package com.colinmoerbe.poecompanion.security.identity

import java.util.UUID

/** Stable Companion-owned user identity, independent of credentials and future OAuth providers. */
data class ApplicationUserId(val value: UUID) {
    companion object {
        fun generate(): ApplicationUserId = ApplicationUserId(UUID.randomUUID())
    }
}
