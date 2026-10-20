package com.colinmoerbe.poecompanion.security.identity

import com.colinmoerbe.poecompanion.security.internal.ApplicationSecurityProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Trusted bootstrap boundary only; absent unless dev fixtures are enabled.
 * Restores the previous context even when fixture validation fails.
 */
@Component
@ConditionalOnProperty(prefix = "poe-companion.dev-fixture", name = ["enabled"], havingValue = "true")
class DevelopmentFixtureActor internal constructor(private val properties: ApplicationSecurityProperties) {
    fun <T> asLegacyUser(action: () -> T): T {
        val previous = SecurityContextHolder.getContext()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(
            properties.username,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        SecurityContextHolder.setContext(context)
        return try {
            action()
        } finally {
            SecurityContextHolder.setContext(previous)
        }
    }
}
