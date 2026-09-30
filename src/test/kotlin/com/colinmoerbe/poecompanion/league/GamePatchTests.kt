package com.colinmoerbe.poecompanion.league

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies exact game-patch validation and representation.
 */
class GamePatchTests {

    @Test
    fun `game patch should render without suffix`() {
        val patch = GamePatch(CompatibilityVersion(3, 29), 3)

        assertThat(patch.toString()).isEqualTo("3.29.3")
    }

    @Test
    fun `game patch should preserve suffix`() {
        val patch = GamePatch(CompatibilityVersion(3, 29), 3, "b")

        assertThat(patch.toString()).isEqualTo("3.29.3b")
    }

    @Test
    fun `negative patch version should be rejected`() {
        assertThatThrownBy {
            GamePatch(CompatibilityVersion(3, 29), -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `blank patch suffix should be rejected`() {
        assertThatThrownBy {
            GamePatch(CompatibilityVersion(3, 29), 3, " ")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
