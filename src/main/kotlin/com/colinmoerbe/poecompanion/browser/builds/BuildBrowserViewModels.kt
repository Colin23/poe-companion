package com.colinmoerbe.poecompanion.browser.builds

import java.util.UUID

/** Readiness is distinct from inability to evaluate the selected build. */
internal enum class BuildBrowserState(val title: String, val explanation: String) {
    UNIQUE_READY("Unique ready", "All enabling and core Unique requirements are satisfied."),
    PARTIAL("Partial", "Enabling requirements are satisfied; some core requirements are incomplete."),
    BLOCKED("Blocked", "At least one enabling requirement group is incomplete."),
    NO_ACTIVE_REVISION("No compatible revision", "No active revision matches the selected compatibility version."),
    NO_ACCOUNT("No environment selected", "Select a game environment to inspect compatible requirements."),
    NO_PERSONAL("Ownership unavailable", "Compatible requirements are known; personal readiness is not evaluated."),
}

internal data class BuildBrowserListEntry(
    val id: UUID,
    val displayName: String,
    val state: BuildBrowserState,
    val explanation: String,
)

internal data class BuildBrowserSection(val state: BuildBrowserState, val entries: List<BuildBrowserListEntry>)

internal data class BuildBrowserListPage(
    val totalCount: Int,
    val sections: List<BuildBrowserSection>,
    val accountSelected: Boolean,
)

internal data class BuildBrowserRequirement(
    val id: UUID,
    val displayName: String,
    val syntheticLabel: Boolean,
    val catalogKnown: Boolean,
    val ownedQuantity: Int?,
    val requiredQuantity: Int,
    val satisfied: Boolean?,
)

internal data class BuildBrowserGroup(
    val importance: String,
    val logic: String,
    val satisfied: Boolean?,
    val requirements: List<BuildBrowserRequirement>,
)

internal data class BuildBrowserDetailPage(
    val id: UUID,
    val displayName: String,
    val state: BuildBrowserState,
    val explanation: String,
    val compatibilityVersion: String?,
    val revisionStatus: String,
    val groups: List<BuildBrowserGroup>,
    val hasRequiredUniqueGroups: Boolean?,
)
