package com.colinmoerbe.poecompanion.browser.acquisitions

import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
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

/** Routing and input validation only; accepted knowledge and probability remain Acquisition-owned. */
@Controller
internal class AcquisitionBrowserController(
    private val readModel: AcquisitionBrowserReadModelService,
    private val attemptsUpdates: AcquisitionAttemptsUpdateService,
) {
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
        val previewUnavailable = preview != null && !page.environmentSelected
        return ModelAndView(
            "acquisitions/detail",
            mapOf(
                "page" to page,
                "username" to principal.name,
                "previewUnavailable" to previewUnavailable,
            ),
        ).apply {
            if (previewUnavailable) setStatus(HttpStatus.CONFLICT)
        }
    }

    @PostMapping("/acquisitions/{id}/attempts")
    fun saveAttempts(
        @PathVariable id: UUID,
        @RequestParam(required = false) count: String?,
        principal: Principal,
    ): ModelAndView {
        val parsedCount = count?.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
            ?: return attemptsError(
                id,
                principal,
                HttpStatus.BAD_REQUEST,
                "Enter a whole available-attempt count of zero or greater. No changes were saved.",
            )
        return writeResponse(id, principal, attemptsUpdates.save(AcquisitionSourceId(id), parsedCount))
    }

    @PostMapping("/acquisitions/{id}/attempts/remove")
    fun removeAttempts(@PathVariable id: UUID, principal: Principal): ModelAndView =
        writeResponse(id, principal, attemptsUpdates.remove(AcquisitionSourceId(id)))

    private fun writeResponse(id: UUID, principal: Principal, result: AcquisitionAttemptsUpdateResult): ModelAndView =
        when (result) {
            AcquisitionAttemptsUpdateResult.UPDATED -> ModelAndView("redirect:/acquisitions/$id")

            AcquisitionAttemptsUpdateResult.UNKNOWN_SOURCE -> attemptsError(
                id,
                principal,
                HttpStatus.NOT_FOUND,
                "This acquisition source is no longer available. No changes were saved.",
                sourceKnown = false,
            )

            AcquisitionAttemptsUpdateResult.NO_ENVIRONMENT_SELECTED -> attemptsError(
                id,
                principal,
                HttpStatus.CONFLICT,
                "Select a game environment before saving available attempts. No changes were made.",
            )

            AcquisitionAttemptsUpdateResult.UNRECOGNIZED_ENVIRONMENT -> attemptsError(
                id,
                principal,
                HttpStatus.CONFLICT,
                "This saved environment is not recognized for new personal data. " +
                    "Choose a curated environment before saving. No changes were made.",
            )
        }

    private fun attemptsError(
        id: UUID,
        principal: Principal,
        status: HttpStatus,
        message: String,
        sourceKnown: Boolean = true,
    ): ModelAndView = ModelAndView(
        "acquisitions/attempts-error",
        mapOf(
            "username" to principal.name,
            "message" to message,
            "sourceId" to id,
            "sourceKnown" to sourceKnown,
        ),
    ).apply { setStatus(status) }
}
