package com.colinmoerbe.poecompanion.browser.environment

import com.colinmoerbe.poecompanion.league.GameEnvironmentOptionService
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import java.security.Principal

/** Supplies the same selected-environment summary to every Browser page. */
@ControllerAdvice(basePackages = ["com.colinmoerbe.poecompanion.browser"])
internal class GameEnvironmentHeaderAdvice(private val environments: GameEnvironmentOptionService) {
    @ModelAttribute("environmentLabel")
    fun environmentLabel(principal: Principal?): String? = if (principal == null) {
        null
    } else {
        environments.current()?.label
    }
}
