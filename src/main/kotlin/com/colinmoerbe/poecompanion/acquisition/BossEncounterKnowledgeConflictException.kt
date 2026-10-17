package com.colinmoerbe.poecompanion.acquisition

/**
 * An accepted encounter change point was inserted or modified since the curator's expected revision.
 */
class BossEncounterKnowledgeConflictException internal constructor(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
