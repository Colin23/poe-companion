package com.colinmoerbe.poecompanion.browser.environment

import com.colinmoerbe.poecompanion.league.GameEnvironmentOption
import com.colinmoerbe.poecompanion.league.GameEnvironmentOptionService
import com.colinmoerbe.poecompanion.league.Ruleset
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import java.security.Principal

/** Handles environment form HTTP navigation; parsing and curated resolution live in dedicated collaborators. */
@Controller
internal class GameEnvironmentController(private val environments: GameEnvironmentOptionService) {
    @GetMapping("/environment")
    fun form(principal: Principal): ModelAndView = renderForm(principal)

    @PostMapping("/environment")
    fun select(@ModelAttribute input: GameEnvironmentFormInput, principal: Principal): ModelAndView {
        val choice = GameEnvironmentFormParser.parse(input)
            ?: return renderForm(principal, input, "Enter a valid game environment.")

        return try {
            environments.select(choice)
            ModelAndView("redirect:/environment")
        } catch (_: IllegalArgumentException) {
            val candidates = environments.findMatching(choice)
            val explanation = when {
                candidates.isEmpty() -> "This combination is not among the curated game environments."

                candidates.size > 1 && choice.leagueDefinitionId == null ->
                    "Several leagues match these settings. Choose a specific league and submit again."

                else -> "The selected league does not match a curated game environment."
            }
            renderForm(principal, input, explanation)
        }
    }

    private fun renderForm(
        principal: Principal,
        submitted: GameEnvironmentFormInput? = null,
        error: String? = null,
    ): ModelAndView {
        val options = environments.findAll()
        val current = environments.current()
        val saved = current?.context?.takeIf { it.ruleset == Ruleset.NORMAL }
        val preferred = options.firstOrNull {
            it.league.id == saved?.leagueDefinitionId &&
                it.compatibilityVersion == saved?.compatibilityVersion
        }
        val initial = preferred ?: options.firstOrNull()
        val input = submitted ?: GameEnvironmentFormInput(
            version = initial?.compatibilityVersion?.toString().orEmpty(),
            type = initial?.league?.type?.name.orEmpty(),
            mortality = initial?.league?.mortality?.name.orEmpty(),
            participation = initial?.league?.participation?.name.orEmpty(),
        )
        val matches = GameEnvironmentFormParser.parse(input)?.let(environments::findMatching).orEmpty()
        val model = mapOf(
            "username" to principal.name,
            "options" to options.map(::toView),
            "versions" to options.map { it.compatibilityVersion.toString() }.distinct(),
            "types" to options.map { it.league.type.name }.distinct(),
            "mortalities" to options.map { it.league.mortality.name }.distinct(),
            "participations" to options.map { it.league.participation.name }.distinct(),
            "selectedVersion" to input.version,
            "selectedType" to input.type,
            "selectedMortality" to input.mortality,
            "selectedParticipation" to input.participation,
            "currentLabel" to current?.label,
            "currentRecognized" to (preferred != null),
            "selectedLeagueId" to (submitted?.leagueId ?: preferred?.league?.id?.value?.toString().orEmpty()),
            "ambiguousOptions" to if (matches.size > 1) matches.map(::toView) else emptyList(),
            "errorMessage" to error,
        )
        return ModelAndView("environment/index", model).apply {
            if (error != null) setStatus(HttpStatus.BAD_REQUEST)
        }
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
