package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RequirementGroupTests {

    @Test
    fun `group should contain one or more requirements`() {
        val first = Requirement(UniqueDefinitionId.generate(), 1)
        val second = Requirement(UniqueDefinitionId.generate(), 2)

        val group = RequirementGroup(RequirementLogic.ALL, listOf(first, second))

        assertThat(group.logic).isEqualTo(RequirementLogic.ALL)
        assertThat(group.requirements).containsExactly(first, second)
    }

    @Test
    fun `group should retain any logic explicitly`() {
        val requirement = Requirement(UniqueDefinitionId.generate(), 1)

        val group = RequirementGroup(RequirementLogic.ANY, listOf(requirement))

        assertThat(group.logic).isEqualTo(RequirementLogic.ANY)
    }

    @Test
    fun `empty group should be rejected`() {
        assertThatThrownBy {
            RequirementGroup(RequirementLogic.ALL, emptyList())
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `same unique should not appear more than once in one group`() {
        val uniqueDefinitionId = UniqueDefinitionId.generate()

        assertThatThrownBy {
            RequirementGroup(
                RequirementLogic.ALL,
                listOf(
                    Requirement(uniqueDefinitionId, 1),
                    Requirement(uniqueDefinitionId, 2),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `exposed requirements should not allow mutation`() {
        val uniqueDefinitionId = UniqueDefinitionId.generate()
        val first = Requirement(uniqueDefinitionId, 1)
        val second = Requirement(UniqueDefinitionId.generate(), 2)
        val group = RequirementGroup(RequirementLogic.ALL, listOf(first, second))

        @Suppress("UNCHECKED_CAST")
        val exposed = group.requirements as MutableList<Requirement>

        assertThatThrownBy {
            exposed.add(Requirement(uniqueDefinitionId, 3))
        }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy {
            exposed.removeAt(0)
        }.isInstanceOf(UnsupportedOperationException::class.java)
        assertThat(group.requirements).containsExactly(first, second)
    }

    @Test
    fun `group should not change when input list is mutated later`() {
        val first = Requirement(UniqueDefinitionId.generate(), 1)
        val input = mutableListOf(first)
        val group = RequirementGroup(RequirementLogic.ALL, input)

        input += Requirement(UniqueDefinitionId.generate(), 1)

        assertThat(group.requirements).containsExactly(first)
    }
}
