package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import java.security.Principal
import java.util.UUID

/** Routing and input validation only; accepted knowledge and probability remain Acquisition-owned. */
@Controller
internal class AcquisitionBrowserController(private val readModel: AcquisitionBrowserReadModelService) {
    @GetMapping("/acquisitions")
    fun list(principal: Principal): ModelAndView = ModelAndView(
        "acquisitions/list",
        mapOf("page" to readModel.list(), "username" to principal.name),
    )

    @GetMapping("/acquisitions/{id}")
    fun detail(
        @PathVariable id: UUID,
        @RequestParam(required = false) attempts: String?,
        principal: Principal,
    ): ModelAndView {
        val preview = attempts?.let { raw ->
            raw.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempts must be a non-negative integer")
        }
        val page = readModel.detail(AcquisitionSourceId(id), preview)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown acquisition source")
        return ModelAndView("acquisitions/detail", mapOf("page" to page, "username" to principal.name))
    }
}
