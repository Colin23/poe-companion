package com.colinmoerbe.poecompanion.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class UniqueDefinitionIdTests {

    @Test
    fun generatedIdsAreDistinct() {
        val first = UniqueDefinitionId.generate()
        val second = UniqueDefinitionId.generate()

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun idsWithTheSameUuidAreEqual() {
        val uuid = UUID.randomUUID()

        assertThat(UniqueDefinitionId(uuid)).isEqualTo(UniqueDefinitionId(uuid))
    }

    @Test
    fun idsWithDifferentUuidsAreNotEqual() {
        assertThat(UniqueDefinitionId(UUID.randomUUID()))
            .isNotEqualTo(UniqueDefinitionId(UUID.randomUUID()))
    }
}
