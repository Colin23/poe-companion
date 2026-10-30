package com.colinmoerbe.poecompanion.acquisition.behavior

/**
 * Curator-verified probability behavior of one source-to-Unique relationship in a game context.
 *
 * Only verified behavior is stored; absent or insufficient knowledge is represented by the resolution result.
 * MODIFIER_DEPENDENT is descriptive only; V0.1 does not calculate such drops.
 */
enum class DropProbabilityBehavior {
    FIXED_INDEPENDENT,
    MODIFIER_DEPENDENT,
}
