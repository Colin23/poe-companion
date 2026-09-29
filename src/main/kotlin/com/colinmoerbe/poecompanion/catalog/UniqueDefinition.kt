package com.colinmoerbe.poecompanion.catalog

/**
 * Represents one canonical mechanically distinct Unique item known to the Companion.
 *
 * A unique definition is reference knowledge, not a physical owned item. Its application-owned identity remains stable
 * as catalog metadata evolves.
 */
class UniqueDefinition(val id: UniqueDefinitionId)
