package com.colinmoerbe.poecompanion.build

/**
 * Signals that a build revision activation lost a race with another lifecycle change.
 */
class BuildRevisionActivationConflictException internal constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
