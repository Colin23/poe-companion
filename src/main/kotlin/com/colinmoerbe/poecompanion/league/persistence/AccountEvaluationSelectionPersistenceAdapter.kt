package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.security.identity.ApplicationUserId
import org.springframework.stereotype.Repository

/** Per-user selection persistence, with DB-enforced ownership/foreign-key integrity. */
@Repository
internal class AccountEvaluationSelectionPersistenceAdapter(
    private val springDataRepository: SpringDataAccountEvaluationSelectionRepository,
) {
    fun save(
        userId: ApplicationUserId,
        accountContextId: AccountContextId,
        compatibilityVersion: CompatibilityVersion,
        gamePatch: GamePatch?,
    ) {
        springDataRepository.save(
            AccountEvaluationSelectionEntity(
                applicationUserId = userId.value,
                accountContextId = accountContextId.value,
                compatibilityVersionMajor = compatibilityVersion.major,
                compatibilityVersionMinor = compatibilityVersion.minor,
                gamePatch = gamePatch?.patch,
                gamePatchSuffix = gamePatch?.suffix,
            ),
        )
    }

    fun find(userId: ApplicationUserId): AccountEvaluationSettings? = springDataRepository
        .findById(userId.value)
        .map { entity ->
            val compatibilityVersion = CompatibilityVersion(
                major = entity.compatibilityVersionMajor,
                minor = entity.compatibilityVersionMinor,
            )
            AccountEvaluationSettings(
                accountContextId = AccountContextId(entity.accountContextId),
                compatibilityVersion = compatibilityVersion,
                gamePatch = entity.gamePatch?.let { patch ->
                    GamePatch(compatibilityVersion = compatibilityVersion, patch = patch, suffix = entity.gamePatchSuffix)
                },
            )
        }.orElse(null)
}
