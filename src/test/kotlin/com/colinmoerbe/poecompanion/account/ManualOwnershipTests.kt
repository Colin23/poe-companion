package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies manual ownership quantity invariants.
 */
class ManualOwnershipTests {

    @Test
    fun `zero manual ownership quantity should be rejected`() {
        assertThatThrownBy {
            ManualOwnership(
                accountContextId = AccountContextId.generate(),
                uniqueDefinitionId = UniqueDefinitionId.generate(),
                quantity = 0,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `negative manual ownership quantity should be rejected`() {
        assertThatThrownBy {
            ManualOwnership(
                accountContextId = AccountContextId.generate(),
                uniqueDefinitionId = UniqueDefinitionId.generate(),
                quantity = -1,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
