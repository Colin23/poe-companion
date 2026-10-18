package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.build.BuildVariantId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import java.security.Principal
import java.util.UUID

/** HTTP routing and error handling only; readiness composition belongs to the read model. */
@Controller
internal class BuildBrowserController(private val readModel: BuildBrowserReadModelService) {
    @GetMapping("/builds")
    fun list(principal: Principal): ModelAndView = ModelAndView(
        "builds/list",
        mapOf("page" to readModel.list(), "username" to principal.name),
    )

    @GetMapping("/builds/{id}")
    fun detail(@PathVariable id: UUID, principal: Principal): ModelAndView {
        val page = readModel.detail(BuildVariantId(id))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown Build variant")
        return ModelAndView("builds/detail", mapOf("page" to page, "username" to principal.name))
    }
}
