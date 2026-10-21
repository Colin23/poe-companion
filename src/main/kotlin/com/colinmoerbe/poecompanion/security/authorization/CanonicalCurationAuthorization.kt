package com.colinmoerbe.poecompanion.security.authorization

import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import com.colinmoerbe.poecompanion.security.identity.CurrentApplicationUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service

/** Authorizes shared canonical writes; ADMIN never gains another user's personal ownership. */
@Service
class CanonicalCurationAuthorization internal constructor(private val currentUser: CurrentApplicationUser) {
    fun requireAdmin() {
        if (currentUser.requireId() != ConfiguredApplicationUsers.ADMIN) {
            throw AccessDeniedException("Canonical curation requires ADMIN")
        }
    }
}
