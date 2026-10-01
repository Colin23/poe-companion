package com.colinmoerbe.poecompanion.league

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Verifies consistency of concrete evaluation contexts.
 */
class EvaluationContextTests {

    @Test
    fun `game patch from another compatibility version should be rejected`() {
        assertThatThrownBy {
            EvaluationContext(
                compatibilityVersion = CompatibilityVersion(3, 30),
                ruleset = Ruleset.NORMAL,
                gamePatch = GamePatch(CompatibilityVersion(3, 29), 3),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
