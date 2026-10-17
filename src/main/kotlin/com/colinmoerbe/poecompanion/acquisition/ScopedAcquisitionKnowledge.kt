package com.colinmoerbe.poecompanion.acquisition

/**
 * Outcome of selecting one accepted knowledge scope for a selected account's league.
 *
 * Missing means neither scope has a timeline; a selected timeline may itself resolve UNKNOWN for the game patch.
 * Two authored timelines are ambiguous even when their current values agree.
 */
sealed interface ScopedAcquisitionKnowledge<out T> {
    data class Selected<T>(val resolution: T) : ScopedAcquisitionKnowledge<T>

    data object Missing : ScopedAcquisitionKnowledge<Nothing>

    data object Ambiguous : ScopedAcquisitionKnowledge<Nothing>
}

/**
 * Both the league-independent and selected-league scopes are independently authoritative.
 * Neither takes precedence over the other.
 */
internal fun <T, R> resolveScopedAcquisitionKnowledge(
    general: T?,
    leagueSpecific: T?,
    resolve: (T) -> R,
): ScopedAcquisitionKnowledge<R> = when {
    general != null && leagueSpecific != null -> ScopedAcquisitionKnowledge.Ambiguous
    general != null -> ScopedAcquisitionKnowledge.Selected(resolve(general))
    leagueSpecific != null -> ScopedAcquisitionKnowledge.Selected(resolve(leagueSpecific))
    else -> ScopedAcquisitionKnowledge.Missing
}
