package com.colinmoerbe.poecompanion.acquisition.overrides

/**
 * A personal confirmation or removal lost an optimistic concurrency decision for its exact key.
 *
 * Do not retry silently: that could overwrite another explicit player action.
 */
class DropRateOverrideConflictException internal constructor(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
