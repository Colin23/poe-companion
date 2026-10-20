package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.security.identity.ApplicationUserId
import com.colinmoerbe.poecompanion.security.identity.CurrentApplicationUser
import org.springframework.stereotype.Service

/**
 * Single application boundary for access to personal PoE contexts, regardless of caller module.
 * Unknown and foreign contexts share one rejection message.
 */
@Service
class AccountContextAccessService internal constructor(
    private val currentUser: CurrentApplicationUser,
    private val accountContexts: AccountContextPersistenceAdapter,
) {
    fun currentUserId(): ApplicationUserId = currentUser.requireId()

    fun requireOwned(id: AccountContextId): AccountContext {
        val user = currentUser.requireId()
        return requireNotNull(accountContexts.findById(id)?.takeIf { it.ownerApplicationUserId == user }) {
            "Account context unavailable"
        }
    }
}
