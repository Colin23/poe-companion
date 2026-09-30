package com.colinmoerbe.poecompanion.league

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies compatibility-version validation and representation.
 */
class CompatibilityVersionTests {

    @Test
    fun `compatibility version should render major and minor components`() {
        val version = CompatibilityVersion(3, 30)

        assertThat(version.toString()).isEqualTo("3.30")
    }

    @Test
    fun `negative major version should be rejected`() {
        assertThatThrownBy { CompatibilityVersion(-1, 30) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `negative minor version should be rejected`() {
        assertThatThrownBy { CompatibilityVersion(3, -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
