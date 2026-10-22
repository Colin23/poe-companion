package com.colinmoerbe.poecompanion.browser.environment

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GameEnvironmentChoice
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import java.util.UUID

/** Browser form input is untrusted text, not a domain command. */
internal data class GameEnvironmentFormInput(
    var version: String = "",
    var type: String = "",
    var mortality: String = "",
    var participation: String = "",
    var ruleset: String = "NORMAL",
    var leagueId: String = "",
)

/** Converts HTTP form values safely; the League service remains the authority for curated validity. */
internal object GameEnvironmentFormParser {
    fun parse(input: GameEnvironmentFormInput): GameEnvironmentChoice? {
        val parts = input.version.split('.')
        if (parts.size != 2) return null

        val major = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val minor = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val type = LeagueType.entries.firstOrNull { it.name == input.type } ?: return null
        val mortality = LeagueMortality.entries.firstOrNull { it.name == input.mortality } ?: return null
        val participation = LeagueParticipation.entries.firstOrNull { it.name == input.participation } ?: return null
        val ruleset = Ruleset.entries.firstOrNull { it.name == input.ruleset } ?: return null
        val leagueId = if (input.leagueId.isBlank()) {
            null
        } else {
            runCatching { LeagueDefinitionId(UUID.fromString(input.leagueId)) }.getOrNull() ?: return null
        }
        return GameEnvironmentChoice(
            compatibilityVersion = CompatibilityVersion(major, minor),
            type = type,
            mortality = mortality,
            participation = participation,
            ruleset = ruleset,
            leagueDefinitionId = leagueId,
        )
    }
}
