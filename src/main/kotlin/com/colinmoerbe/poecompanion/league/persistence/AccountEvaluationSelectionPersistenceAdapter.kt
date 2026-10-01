package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GamePatch
import org.springframework.stereotype.Repository

/**
 * Persists the application's single current account/evaluation selection.
 */
@Repository
internal class AccountEvaluationSelectionPersistenceAdapter(
    private val springDataRepository: SpringDataAccountEvaluationSelectionRepository,
) {
    fun save(
        accountContextId: AccountContextId,
        compatibilityVersion: CompatibilityVersion,
        gamePatch: GamePatch?,
    ) {
        springDataRepository.save(
            AccountEvaluationSelectionEntity(
                singletonId = SINGLETON_ID,
                accountContextId = accountContextId.value,
                compatibilityVersionMajor = compatibilityVersion.major,
                compatibilityVersionMinor = compatibilityVersion.minor,
                gamePatch = gamePatch?.patch,
                gamePatchSuffix = gamePatch?.suffix,
            ),
        )
    }

    fun find(): AccountEvaluationSettings? =
        springDataRepository
            .findById(SINGLETON_ID)
            .map { entity ->
                val compatibilityVersion =
                    CompatibilityVersion(
                        major = entity.compatibilityVersionMajor,
                        minor = entity.compatibilityVersionMinor,
                    )
                AccountEvaluationSettings(
                    accountContextId = AccountContextId(entity.accountContextId),
                    compatibilityVersion = compatibilityVersion,
                    gamePatch =
                        entity.gamePatch?.let { patch ->
                            GamePatch(
                                compatibilityVersion = compatibilityVersion,
                                patch = patch,
                                suffix = entity.gamePatchSuffix,
                            )
                        },
                )
            }.orElse(null)

    private companion object {
        const val SINGLETON_ID = 1
    }
}
