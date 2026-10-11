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
    fun `patch ordering should compare numeric patch before suffix`() {
        val version = CompatibilityVersion(3, 30)

        assertThat(GamePatch(version, 1).compareWithinCompatibilityVersion(GamePatch(version, 2))).isNegative()
        assertThat(GamePatch(version, 2).compareWithinCompatibilityVersion(GamePatch(version, 1))).isPositive()
    }

    @Test
    fun `patch ordering should place unsuffixed release before lexical suffixes`() {
        val version = CompatibilityVersion(3, 30)
        val base = GamePatch(version, 1)
        val patchB = GamePatch(version, 1, "b")
        val patchC = GamePatch(version, 1, "c")

        assertThat(base.compareWithinCompatibilityVersion(patchB)).isNegative()
        assertThat(patchB.compareWithinCompatibilityVersion(patchC)).isNegative()
    }

    @Test
    fun `patch ordering should reject different compatibility versions`() {
        assertThatThrownBy {
            GamePatch(CompatibilityVersion(3, 30), 1)
                .compareWithinCompatibilityVersion(GamePatch(CompatibilityVersion(3, 31), 1))
        }.isInstanceOf(IllegalArgumentException::class.java)
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
