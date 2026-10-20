package com.colinmoerbe.poecompanion.security.identity

import com.colinmoerbe.poecompanion.security.internal.ApplicationSecurityProperties
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * V0.1 single-login identity bridge. Requires a real authenticated principal; future auth providers
 * can map their own external subject to an ApplicationUserId without changing ownership semantics.
 */
@Component
class CurrentApplicationUser internal constructor(private val properties: ApplicationSecurityProperties) {
    fun requireId(): ApplicationUserId {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication?.isAuthenticated != true || authentication.name != properties.username) {
            throw AccessDeniedException("Application authentication required")
        }
        return ConfiguredApplicationUsers.USER
    }
}
