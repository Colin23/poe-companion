package com.colinmoerbe.poecompanion.acquisition.overrides.internal

import com.colinmoerbe.poecompanion.acquisition.overrides.DropRateOverride
import com.colinmoerbe.poecompanion.acquisition.overrides.DropRateOverrideKey
import com.colinmoerbe.poecompanion.acquisition.overrides.DropRateOverrideRevisionId

/**
 * Internal owner-scoped persistence operations; only current confirmations are exposed.
 *
 * Expected revision identity enforces compare-and-swap replacement and removal.
 */
internal interface DropRateOverridePersistencePort {
    fun findCurrent(key: DropRateOverrideKey): DropRateOverride?

    fun confirm(confirmation: DropRateOverride, expectedRevisionId: DropRateOverrideRevisionId?)

    fun remove(key: DropRateOverrideKey, expectedRevisionId: DropRateOverrideRevisionId)
}
