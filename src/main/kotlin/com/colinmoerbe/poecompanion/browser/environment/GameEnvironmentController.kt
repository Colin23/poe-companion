package com.colinmoerbe.poecompanion.browser.environment

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GameEnvironmentChoice
import com.colinmoerbe.poecompanion.league.GameEnvironmentOption
import com.colinmoerbe.poecompanion.league.GameEnvironmentOptionService
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import java.security.Principal
import java.util.UUID

/** Game selection is independent of personal collection ownership. */
@Controller
internal class GameEnvironmentController(private val environments: GameEnvironmentOptionService) {
    @GetMapping("/environment")
    fun form(principal: Principal): ModelAndView {
        val options = environments.findAll()
        val current = environments.current()
        val preferred = options.firstOrNull {
            it.league.id == current?.context?.leagueDefinitionId &&
                it.compatibilityVersion == current?.context?.compatibilityVersion
        } ?: options.firstOrNull()
        return ModelAndView(
            "environment/index",
            mapOf(
                "username" to principal.name,
                "options" to options.map(::toView),
                "versions" to options.map { it.compatibilityVersion.toString() }.distinct(),
                "types" to options.map { it.league.type.name }.distinct(),
                "mortalities" to options.map { it.league.mortality.name }.distinct(),
                "participations" to options.map { it.league.participation.name }.distinct(),
                "selectedVersion" to preferred?.compatibilityVersion?.toString(),
                "selectedType" to preferred?.league?.type?.name,
                "selectedMortality" to preferred?.league?.mortality?.name,
                "selectedParticipation" to preferred?.league?.participation?.name,
                "currentLabel" to current?.label,
                "currentRecognized" to options.any {
                    it.league.id == current?.context?.leagueDefinitionId &&
                        it.compatibilityVersion == current?.context?.compatibilityVersion
                },
                "selectedLeagueId" to preferred?.league?.id?.value?.toString(),
            ),
        )
    }

    @Suppress("ThrowsCount")
    @PostMapping("/environment")
    fun select(
        @RequestParam version: String,
        @RequestParam type: String,
        @RequestParam mortality: String,
        @RequestParam participation: String,
        @RequestParam ruleset: String,
        @RequestParam(required = false) leagueId: String?,
    ): ModelAndView {
        val pieces = Regex("([0-9]+)\\.([0-9]+)").matchEntire(version)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown compatibility version")
        val choice = try {
            GameEnvironmentChoice(
                compatibilityVersion = CompatibilityVersion(
                    pieces.groupValues[1].toInt(),
                    pieces.groupValues[2].toInt(),
                ),
                type = enumValueOf<LeagueType>(type),
                mortality = enumValueOf<LeagueMortality>(mortality),
                participation = enumValueOf<LeagueParticipation>(participation),
                ruleset = enumValueOf(ruleset),
                leagueDefinitionId = leagueId?.takeIf { it.isNotBlank() }?.let {
                    LeagueDefinitionId(UUID.fromString(it))
                },
            )
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid game environment")
        }
        try {
            environments.select(choice)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or ambiguous curated game environment")
        }
        return ModelAndView("redirect:/environment")
    }

    private fun toView(option: GameEnvironmentOption): SelectableGameEnvironmentView = SelectableGameEnvironmentView(
        version = option.compatibilityVersion.toString(),
        type = option.league.type.name,
        mortality = option.league.mortality.name,
        participation = option.league.participation.name,
        name = option.league.name,
        leagueId = option.league.id.value.toString(),
    )
}

internal data class SelectableGameEnvironmentView(
    val version: String,
    val type: String,
    val mortality: String,
    val participation: String,
    val name: String,
    val leagueId: String,
)
