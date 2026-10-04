package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RequirementTests {

    @Test
    fun `positive required quantity should be valid`() {
        val requirement = Requirement(UniqueDefinitionId.generate(), 2)

        assertThat(requirement.requiredQuantity).isEqualTo(2)
    }

    @Test
    fun `zero required quantity should be rejected`() {
        assertThatThrownBy {
            Requirement(UniqueDefinitionId.generate(), 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `negative required quantity should be rejected`() {
        assertThatThrownBy {
            Requirement(UniqueDefinitionId.generate(), -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
