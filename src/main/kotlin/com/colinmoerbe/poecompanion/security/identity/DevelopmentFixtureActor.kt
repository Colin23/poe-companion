package com.colinmoerbe.poecompanion.security.identity

import com.colinmoerbe.poecompanion.security.internal.ApplicationSecurityProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Trusted bootstrap-only actor. Registered only with the development fixture opt-in property.
 * Every temporary context is restored even if the enclosed initialization fails.
 */
@Component
@ConditionalOnProperty(prefix = "poe-companion.dev-fixture", name = ["enabled"], havingValue = "true")
class DevelopmentFixtureActor internal constructor(private val properties: ApplicationSecurityProperties) {
    fun <T> asLegacyUser(action: () -> T): T = asActor(properties.user.username, "ROLE_USER", action)

    fun <T> asAdmin(action: () -> T): T = asActor(properties.admin.username, "ROLE_ADMIN", action)

    private fun <T> asActor(username: String, authority: String, action: () -> T): T {
        val previous = SecurityContextHolder.getContext()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(
            username,
            null,
            listOf(SimpleGrantedAuthority(authority)),
        )
        SecurityContextHolder.setContext(context)
        return try {
            action()
        } finally {
            SecurityContextHolder.setContext(previous)
        }
    }
}
