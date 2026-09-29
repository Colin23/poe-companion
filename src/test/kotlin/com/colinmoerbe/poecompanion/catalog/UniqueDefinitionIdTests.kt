package com.colinmoerbe.poecompanion.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Verifies generation and value semantics for [UniqueDefinitionId].
 */
class UniqueDefinitionIdTests {

    @Test
    fun `two generated unique definition ids should not be equal`() {
        val first = UniqueDefinitionId.generate()
        val second = UniqueDefinitionId.generate()

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `reconstructed unique definition id should equal the original`() {
        val original = UniqueDefinitionId.generate()

        // Reconstruct a different object with the same UUID to verify value equality.
        val reconstructed = UniqueDefinitionId(original.value)

        assertThat(reconstructed).isEqualTo(original)
    }
}
