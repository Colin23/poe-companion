package com.colinmoerbe.poecompanion.acquisition.sourceidentity

/**
 * The requested BossDefinition identity conflicts with existing metadata, or a likely duplicate name requires review.
 */
class BossDefinitionCreationConflictException internal constructor(message: String) : RuntimeException(message)
