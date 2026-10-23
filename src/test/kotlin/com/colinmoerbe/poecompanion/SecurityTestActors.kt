package com.colinmoerbe.poecompanion

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Test-only scope for seeding shared curator knowledge.
 * Never changes an enclosing USER principal beyond its callback.
 */
internal object SecurityTestActors {
    fun <T> asUser(action: () -> T): T = asActor("test-user", "ROLE_USER", action)

    fun <T> asAdmin(action: () -> T): T = asActor("test-admin", "ROLE_ADMIN", action)

    private fun <T> asActor(username: String, role: String, action: () -> T): T {
        val previous = SecurityContextHolder.getContext()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(
            username,
            null,
            listOf(SimpleGrantedAuthority(role)),
        )
        SecurityContextHolder.setContext(context)
        return try {
            action()
        } finally {
            SecurityContextHolder.setContext(previous)
        }
    }
}
