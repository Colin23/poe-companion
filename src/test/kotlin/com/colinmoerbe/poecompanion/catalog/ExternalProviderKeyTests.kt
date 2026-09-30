package com.colinmoerbe.poecompanion.catalog

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies the minimum validity contract of provider-owned identity keys.
 */
class ExternalProviderKeyTests {

    @Test
    fun `blank external provider key should be rejected`() {
        assertThatThrownBy { ExternalProviderKey("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
