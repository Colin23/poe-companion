package com.colinmoerbe.poecompanion.acquisition.relationship

/**
 * The exact accepted relationship change point was created or edited after the curator's expected version.
 */
class DropRelationshipKnowledgeConflictException internal constructor(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
