package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies manual ownership quantity invariants.
 */
class ManualOwnershipTests {

    @Test
    fun `zero owned quantity should be valid`() {
        val ownership =
            ManualOwnership(
                accountContextId = AccountContextId.generate(),
                uniqueDefinitionId = UniqueDefinitionId.generate(),
                quantity = 0,
            )

        assertThat(ownership.quantity).isZero()
    }

    @Test
    fun `negative owned quantity should be rejected`() {
        assertThatThrownBy {
            ManualOwnership(
                accountContextId = AccountContextId.generate(),
                uniqueDefinitionId = UniqueDefinitionId.generate(),
                quantity = -1,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
