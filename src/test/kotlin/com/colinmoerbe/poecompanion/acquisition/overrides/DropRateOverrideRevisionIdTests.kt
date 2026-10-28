package com.colinmoerbe.poecompanion.acquisition.overrides

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DropRateOverrideRevisionIdTests {

    @Test
    fun `generated revision identities should be distinct and UUID-backed`() {
        val first = DropRateOverrideRevisionId.generate()
        val second = DropRateOverrideRevisionId.generate()

        assertThat(first).isNotEqualTo(second)
        assertThat(DropRateOverrideRevisionId(first.value)).isEqualTo(first)
    }
}
