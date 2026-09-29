package com.colinmoerbe.poecompanion.catalog

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UniqueDefinitionIdTests {

    @Test
    fun generatesDistinctIds() {
        val first = UniqueDefinitionId.generate()
        val second = UniqueDefinitionId.generate()

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun usesWrappedUuidForValueEquality() {
        val uuid = UUID.fromString("7cb15db6-15a3-4b5e-aea1-23abf28a41fb")

        assertThat(UniqueDefinitionId(uuid)).isEqualTo(UniqueDefinitionId(uuid))
    }
}
