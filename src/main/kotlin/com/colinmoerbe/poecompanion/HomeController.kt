package com.colinmoerbe.poecompanion

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.ModelAndView
import java.security.Principal

@Controller
internal class HomeController {
    @GetMapping("/")
    fun home(principal: Principal): ModelAndView = ModelAndView(
        "index",
        mapOf(
            "page" to
                HomePageViewModel(
                    title = "PoE Companion",
                    username = principal.name,
                ),
        ),
    )
}

internal data class HomePageViewModel(val title: String, val username: String)
