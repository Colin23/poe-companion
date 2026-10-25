package com.colinmoerbe.poecompanion.browser.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AcquisitionBrowserKnowledgeLabelsTests {
    @Test
    fun `cumulative chance formatting never disguises a small positive or sub-certain result`() {
        assertThat(AcquisitionBrowserKnowledgeLabels.cumulativePercent(0.0)).isEqualTo("0.0%")
        assertThat(AcquisitionBrowserKnowledgeLabels.cumulativePercent(0.000001)).isEqualTo("<0.1%")
        assertThat(AcquisitionBrowserKnowledgeLabels.cumulativePercent(0.718)).isEqualTo("71.8%")
        assertThat(AcquisitionBrowserKnowledgeLabels.cumulativePercent(0.999999)).isEqualTo(">99.9%")
        assertThat(AcquisitionBrowserKnowledgeLabels.cumulativePercent(1.0)).isEqualTo("100.0%")
    }

    @Test
    fun `no game environment leaves a source identity known but its name unevaluated`() {
        assertThat(AcquisitionBrowserKnowledgeLabels.sourceNameResolved(null)).isFalse()
        assertThat(AcquisitionBrowserKnowledgeLabels.sourceNameExplanation(null)).isEqualTo("Name not evaluated")
        assertThat(AcquisitionBrowserKnowledgeLabels.readableSourceAvailability(null)).isEqualTo("Not evaluated")
    }
}
