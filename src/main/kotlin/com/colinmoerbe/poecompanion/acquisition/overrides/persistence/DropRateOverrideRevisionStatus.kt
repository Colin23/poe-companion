package com.colinmoerbe.poecompanion.acquisition.overrides.persistence

/**
 * Persistence-only current/historical state. The confirmed domain revision has no mutable STALE state.
 */
internal enum class DropRateOverrideRevisionStatus {
    ACTIVE,
    SUPERSEDED,
    REMOVED,
}
