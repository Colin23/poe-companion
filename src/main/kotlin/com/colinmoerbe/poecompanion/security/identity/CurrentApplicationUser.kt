package com.colinmoerbe.poecompanion.security.identity

import com.colinmoerbe.poecompanion.security.internal.ApplicationSecurityProperties
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Temporary V0.1 configured-login bridge. The application UUID is independent of the username and the role.
 * Both the configured username and its expected authenticated authority must match.
 */
@Component
class CurrentApplicationUser internal constructor(private val properties: ApplicationSecurityProperties) {
    fun requireId(): ApplicationUserId {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication?.isAuthenticated != true) {
            throw AccessDeniedException("Application authentication required")
        }
        val authorities = authentication.authorities.map { it.authority }.toSet()
        return when {
            authentication.name == properties.user.username && "ROLE_USER" in authorities &&
                "ROLE_ADMIN" !in authorities -> ConfiguredApplicationUsers.USER
            authentication.name == properties.admin.username && "ROLE_ADMIN" in authorities &&
                "ROLE_USER" !in authorities -> ConfiguredApplicationUsers.ADMIN
            else -> throw AccessDeniedException("Application authentication required")
        }
    }
}
