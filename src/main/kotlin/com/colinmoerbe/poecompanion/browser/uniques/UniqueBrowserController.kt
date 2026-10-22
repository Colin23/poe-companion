package com.colinmoerbe.poecompanion.browser.uniques

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import java.security.Principal
import java.util.UUID

/** Only HTTP mapping and validation live here; the read model supplies presentation data. */
@Controller
internal class UniqueBrowserController(
    private val readModel: UniqueBrowserReadModelService,
    private val ownershipUpdates: UniqueOwnershipUpdateService,
) {
    @GetMapping("/uniques")
    fun list(@RequestParam(required = false) filter: String?, principal: Principal): ModelAndView {
        val collectionFilter =
            UniqueCollectionFilter.parse(filter ?: "all")
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown collection filter")

        val page = readModel.list(collectionFilter)
        if (!page.accountSelected && collectionFilter != UniqueCollectionFilter.ALL) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Ownership filters require a saved personal collection",
            )
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

    @PostMapping("/uniques/{id}/ownership")
    fun updateOwnership(
        @PathVariable id: UUID,
        @RequestParam(required = false) quantity: String?,
        principal: Principal,
    ): ModelAndView {
        val ownedQuantity = quantity?.toIntOrNull()?.takeIf { it >= 0 }
            ?: return ownershipError(
                id,
                principal,
                HttpStatus.BAD_REQUEST,
                "Enter a whole owned quantity of zero or greater. No changes were saved.",
            )

        return when (ownershipUpdates.update(UniqueDefinitionId(id), ownedQuantity)) {
            UniqueOwnershipUpdateResult.UPDATED -> ModelAndView("redirect:/uniques/$id")

            UniqueOwnershipUpdateResult.NO_ACCOUNT_SELECTED -> ownershipError(
                id,
                principal,
                HttpStatus.CONFLICT,
                "No personal collection is available for this environment. Ownership cannot be saved yet." +
                    "No changes were made.",
            )

            UniqueOwnershipUpdateResult.UNKNOWN_UNIQUE -> ownershipError(
                id,
                principal,
                HttpStatus.NOT_FOUND,
                "This Unique is no longer available in the catalog. No changes were saved.",
                uniqueKnown = false,
            )
        }
    }

    private fun ownershipError(
        id: UUID,
        principal: Principal,
        httpStatus: HttpStatus,
        message: String,
        uniqueKnown: Boolean = true,
    ): ModelAndView = ModelAndView(
        "uniques/ownership-error",
        mapOf(
            "username" to principal.name,
            "message" to message,
            "uniqueId" to id,
            "uniqueKnown" to uniqueKnown,
        ),
    ).apply { setStatus(httpStatus) }
}
