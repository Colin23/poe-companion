package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies the normalized sparse semantics of current ownership.
 */
class CurrentOwnershipTests {

    @Test
    fun `missing unique should have zero owned quantity`() {
        val ownedUnique = UniqueDefinitionId.generate()
        val missingUnique = UniqueDefinitionId.generate()
        val ownership =
            CurrentOwnership(
                accountContextId = AccountContextId.generate(),
                quantities = mapOf(ownedUnique to 2),
            )

        assertThat(ownership.quantityOf(ownedUnique)).isEqualTo(2)
        assertThat(ownership.quantityOf(missingUnique)).isZero()
    }

    @Test
    fun `explicit zero quantity should be normalized out of sparse ownership`() {
        val uniqueDefinitionId = UniqueDefinitionId.generate()
        val ownership =
            CurrentOwnership(
                accountContextId = AccountContextId.generate(),
                quantities = mapOf(uniqueDefinitionId to 0),
            )

        assertThat(ownership.quantities).doesNotContainKey(uniqueDefinitionId)
        assertThat(ownership.quantityOf(uniqueDefinitionId)).isZero()
    }

    @Test
    fun `negative quantity should be rejected`() {
        assertThatThrownBy {
            CurrentOwnership(
                accountContextId = AccountContextId.generate(),
                quantities = mapOf(UniqueDefinitionId.generate() to -1),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `ownership should not change when the input map is mutated later`() {
        val uniqueDefinitionId = UniqueDefinitionId.generate()
        val input = mutableMapOf(uniqueDefinitionId to 1)
        val ownership =
            CurrentOwnership(
                accountContextId = AccountContextId.generate(),
                quantities = input,
            )

        input[uniqueDefinitionId] = 99

        assertThat(ownership.quantityOf(uniqueDefinitionId)).isEqualTo(1)
    }
}
