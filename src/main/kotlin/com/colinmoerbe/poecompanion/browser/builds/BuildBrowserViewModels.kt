package com.colinmoerbe.poecompanion.browser.builds

import java.util.UUID

/** Readiness is distinct from inability to evaluate the selected build. */
internal enum class BuildBrowserState(val title: String, val explanation: String) {
    UNIQUE_READY("Unique ready", "All enabling and core Unique requirements are satisfied."),
    PARTIAL("Partial", "Enabling requirements are satisfied; some core requirements are incomplete."),
    BLOCKED("Blocked", "At least one enabling requirement group is incomplete."),
    NO_ACTIVE_REVISION("No compatible revision", "No active requirements match the selected compatibility version."),
    NO_ACCOUNT("No environment selected", "Select a game environment to inspect compatible requirements."),
    NO_PERSONAL("Ownership unavailable", "Compatible requirements are known; personal readiness is not evaluated."),
}

/** Section order is a display concern; it does not introduce a new domain readiness state. */
internal enum class BuildBrowserListCategory(
    val title: String,
    val explanation: String,
    val cssClass: String,
    val symbol: String,
) {
    NO_REQUIRED("No required Uniques", "These Builds have no mandatory Unique item requirements.", "no-required", "—"),
    UNIQUE_READY("Unique ready", "You own all enabling and core Uniques required for these Builds.", "ready", "✓"),
    PARTIAL("Partial", "Enabling requirements are met, but some core Uniques are still missing.", "partial", "◐"),
    BLOCKED("Blocked", "At least one essential enabling requirement is missing.", "blocked", "!"),
    NO_PERSONAL("Ownership not evaluated", "Requirements are available, but your personal collection is not.", "unknown", "?"),
    NO_ACTIVE_REVISION("No compatible requirements", "No active requirements match the selected game version.", "unknown", "?"),
    NO_ACCOUNT("Select a game environment", "Choose a game environment to see applicable requirements.", "unknown", "?"),
}

internal data class BuildBrowserListEntry(
    val id: UUID,
    val displayName: String,
    val state: BuildBrowserState,
    val category: BuildBrowserListCategory,
)

internal data class BuildBrowserSection(val category: BuildBrowserListCategory, val entries: List<BuildBrowserListEntry>)

internal data class BuildBrowserListPage(
    val totalCount: Int,
    val sections: List<BuildBrowserSection>,
    val environmentSelected: Boolean,
)

internal data class BuildBrowserRequirement(
    val id: UUID,
    val displayName: String,
    val syntheticLabel: Boolean,
    val catalogKnown: Boolean,
    val ownedQuantity: Int?,
    val requiredQuantity: Int,
    val satisfied: Boolean?,
) {
    val quantityLabel: String
        get() = if (ownedQuantity == null) {
            "Requires $requiredQuantity ${if (requiredQuantity == 1) "copy" else "copies"}"
        } else {
            "$ownedQuantity / $requiredQuantity owned"
        }

    val statusLabel: String
        get() = when (satisfied) {
            true -> "Complete"
            false -> {
                val missing = requiredQuantity - requireNotNull(ownedQuantity)
                "Missing $missing ${if (missing == 1) "copy" else "copies"}"
            }
            null -> "Ownership not evaluated"
        }

    val statusCssClass: String
        get() = when (satisfied) {
            true -> "ready"
            false -> "missing"
            null -> "unknown"
        }
}

internal data class BuildBrowserGroup(
    val importance: String,
    val logic: String,
    val satisfied: Boolean?,
    val requirements: List<BuildBrowserRequirement>,
) {
    val title: String
        get() = when (importance) {
            "ENABLING" -> "Build-enabling Uniques"
            "CORE" -> "Core Uniques"
            "UPGRADE" -> "Upgrade options"
            else -> "Unique requirements"
        }

    val guidance: String?
        get() = when {
            logic == "ANY" -> "Owning any one of these Uniques completes this group. You can still collect more than one."
            requirements.size > 1 -> "All listed Uniques are needed to complete this group."
            else -> null
        }

    val statusLabel: String
        get() = when (satisfied) {
            null -> "Not evaluated"
            true -> if (importance == "UPGRADE") "Collected" else "Complete"
            false -> if (importance == "UPGRADE") "Not collected yet" else "Missing items"
        }

    val statusCssClass: String
        get() = when {
            satisfied == null -> "unknown"
            importance == "UPGRADE" -> "optional"
            satisfied -> "ready"
            else -> "missing"
        }
}

internal data class BuildBrowserDetailPage(
    val id: UUID,
    val displayName: String,
    val state: BuildBrowserState,
    val explanation: String,
    val compatibilityVersion: String?,
    val groups: List<BuildBrowserGroup>,
    val hasRequiredUniqueGroups: Boolean?,
) {
    val statusLabel: String
        get() = if (hasRequiredUniqueGroups == false) "No required Uniques" else state.title

    val statusCssClass: String
        get() = when {
            hasRequiredUniqueGroups == false -> "no-required"
            state == BuildBrowserState.UNIQUE_READY -> "ready"
            state == BuildBrowserState.PARTIAL -> "partial"
            state == BuildBrowserState.BLOCKED -> "blocked"
            else -> "unknown"
        }

    val compatibilityMessage: String
        get() = when (state) {
            BuildBrowserState.NO_ACCOUNT -> "Select a game environment to view Build requirements."
            BuildBrowserState.NO_ACTIVE_REVISION ->
                "No active Build requirements for version ${compatibilityVersion ?: "the selected environment"}."
            else -> "Requirements available for version ${compatibilityVersion ?: "the selected environment"}."
        }

    val hasOptionalGroups: Boolean
        get() = groups.any { it.importance == "UPGRADE" }

    val hasSyntheticLabels: Boolean
        get() = groups.any { group -> group.requirements.any { it.syntheticLabel } }
}
