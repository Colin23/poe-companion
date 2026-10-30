package com.colinmoerbe.poecompanion.acquisition.behavior

/**
 * Curator-verified probability behavior of one source-to-Unique relationship in a game context.
 *
 * UNKNOWN is not authored: absent or insufficient knowledge resolves to unknown.
 * MODIFIER_DEPENDENT is descriptive only; V0.1 does not calculate such drops.
 */
enum class DropProbabilityBehavior {
    FIXED_INDEPENDENT,
    MODIFIER_DEPENDENT,
}
