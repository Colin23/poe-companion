package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId

/**
 * One required Unique and quantity inside a build requirement group.
 */
data class Requirement(val uniqueDefinitionId: UniqueDefinitionId, val requiredQuantity: Int) {
    init {
        require(requiredQuantity > 0) { "Required quantity must be positive" }
    }
}
