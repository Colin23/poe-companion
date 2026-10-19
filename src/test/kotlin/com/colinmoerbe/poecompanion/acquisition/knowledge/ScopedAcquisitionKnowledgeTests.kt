package com.colinmoerbe.poecompanion.acquisition.knowledge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ScopedAcquisitionKnowledgeTests {
    @Test
    fun `one general timeline should be selected`() {
        assertThat(resolveScopedAcquisitionKnowledge("general", null, String::uppercase))
            .isEqualTo(ScopedAcquisitionKnowledge.Selected("GENERAL"))
    }

    @Test
    fun `one league-specific timeline should be selected`() {
        assertThat(resolveScopedAcquisitionKnowledge(null, "league", String::uppercase))
            .isEqualTo(ScopedAcquisitionKnowledge.Selected("LEAGUE"))
    }

    @Test
    fun `two authored scopes should be ambiguous even when the values agree`() {
        val resolution = resolveScopedAcquisitionKnowledge("same", "same") {
            error("Ambiguous knowledge must not select either timeline")
        }

        assertThat(resolution).isEqualTo(ScopedAcquisitionKnowledge.Ambiguous)
    }

    @Test
    fun `absence of authored knowledge should stay missing`() {
        val resolution = resolveScopedAcquisitionKnowledge<String, String>(null, null, String::uppercase)

        assertThat(resolution).isEqualTo(ScopedAcquisitionKnowledge.Missing)
    }
}
