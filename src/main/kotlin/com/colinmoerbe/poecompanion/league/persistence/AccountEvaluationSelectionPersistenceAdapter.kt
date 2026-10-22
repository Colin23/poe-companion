package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.security.identity.ApplicationUserId
import org.springframework.stereotype.Repository

/** Per-user game selection, independent of AccountContext ownership and personal storage. */
@Repository
internal class AccountEvaluationSelectionPersistenceAdapter(
    private val springDataRepository: SpringDataAccountEvaluationSelectionRepository,
) {
    fun save(userId: ApplicationUserId, context: EvaluationContext) {
        val leagueId = requireNotNull(context.leagueDefinitionId)
        springDataRepository.save(
            AccountEvaluationSelectionEntity(
                applicationUserId = userId.value,
                leagueDefinitionId = leagueId.value,
                ruleset = context.ruleset,
                compatibilityVersionMajor = context.compatibilityVersion.major,
                compatibilityVersionMinor = context.compatibilityVersion.minor,
                gamePatch = context.gamePatch?.patch,
                gamePatchSuffix = context.gamePatch?.suffix,
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
                leagueDefinitionId = LeagueDefinitionId(entity.leagueDefinitionId),
                ruleset = entity.ruleset,
                compatibilityVersion = compatibilityVersion,
                gamePatch = entity.gamePatch?.let { patch ->
                    GamePatch(
                        compatibilityVersion = compatibilityVersion,
                        patch = patch,
                        suffix = entity.gamePatchSuffix,
                    )
                },
            )
        }.orElse(null)
}
