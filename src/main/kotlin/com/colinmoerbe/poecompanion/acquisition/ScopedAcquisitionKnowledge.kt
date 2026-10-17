package com.colinmoerbe.poecompanion.acquisition

/**
 * Outcome of selecting one accepted knowledge scope for a selected account's league.
 *
 * Missing means neither scope has a timeline; a selected timeline may itself resolve UNKNOWN for the game patch.
 * Two authored timelines are ambiguous even when their current values agree.
 *
 * The covariant `out T` lets the value-less Missing and Ambiguous cases use `Nothing` (no possible value)
 * while still fitting any concrete resolution type.
 */
sealed interface ScopedAcquisitionKnowledge<out T> {
    data class Selected<T>(val resolution: T) : ScopedAcquisitionKnowledge<T>

    data object Missing : ScopedAcquisitionKnowledge<Nothing>

    data object Ambiguous : ScopedAcquisitionKnowledge<Nothing>
}

/**
 * Selects one authored timeline and evaluates it without giving either league scope precedence.
 *
 * T is the input timeline type; R is its resolved result type. The caller supplies [resolve] to convert
 * T to R (for example, `timeline.resolve(context)`), so the same selection rule works for encounter and
 * drop-relationship knowledge.
 *
 * Detect ambiguity **before** calling [resolve]: equal values, or a timeline that currently resolves UNKNOWN,
 * must not cause an implicit fallback to the other independently authored scope.
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
