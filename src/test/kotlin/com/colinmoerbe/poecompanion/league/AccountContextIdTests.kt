package com.colinmoerbe.poecompanion.league

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies application-owned account-context identity semantics.
 */
class AccountContextIdTests {

    @Test
    fun `generated account context identities should be distinct`() {
        assertThat(AccountContextId.generate()).isNotEqualTo(AccountContextId.generate())
    }
}
