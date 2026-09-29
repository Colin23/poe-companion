package com.colinmoerbe.poecompanion.catalog

import java.util.UUID

data class UniqueDefinitionId(
    val value: UUID,
) {
    companion object {
        fun generate(): UniqueDefinitionId = UniqueDefinitionId(UUID.randomUUID())
    }
}
