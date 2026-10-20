package com.colinmoerbe.poecompanion.security.identity

import java.util.UUID

/**
 * Stable identities for the temporary V0.1 configured users.
 * Database migration V018 seeds the same UUIDs; usernames, roles and provider identities are not keys.
 */
object ConfiguredApplicationUsers {
    val USER: ApplicationUserId = ApplicationUserId(UUID.fromString("00000000-0000-4000-8000-000000006001"))
    val ADMIN: ApplicationUserId = ApplicationUserId(UUID.fromString("00000000-0000-4000-8000-000000006002"))
}
