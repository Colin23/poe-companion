package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import java.security.Principal
import java.util.UUID

/** Only HTTP mapping and validation live here; the read model supplies presentation data. */
@Controller
internal class UniqueBrowserController(private val readModel: UniqueBrowserReadModelService) {
    @GetMapping("/uniques")
    fun list(@RequestParam(required = false) filter: String?, principal: Principal): ModelAndView {
        val collectionFilter =
            UniqueCollectionFilter.parse(filter ?: "all")
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown collection filter")

        val page = readModel.list(collectionFilter)
        if (!page.accountSelected && collectionFilter != UniqueCollectionFilter.ALL) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an account before filtering by ownership")
        }

        return ModelAndView(
            "uniques/list",
            mapOf("page" to page, "username" to principal.name),
        )
    }

    @GetMapping("/uniques/{id}")
    fun detail(@PathVariable id: UUID, principal: Principal): ModelAndView {
        val page =
            readModel.detail(UniqueDefinitionId(id))
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown Unique")

        return ModelAndView(
            "uniques/detail",
            mapOf("page" to page, "username" to principal.name),
        )
    }
}
