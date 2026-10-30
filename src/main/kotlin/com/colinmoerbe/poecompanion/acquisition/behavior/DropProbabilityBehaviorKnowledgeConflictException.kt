package com.colinmoerbe.poecompanion.acquisition.behavior

/** An exact probability-behavior change point was concurrently created or edited. */
class DropProbabilityBehaviorKnowledgeConflictException internal constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
