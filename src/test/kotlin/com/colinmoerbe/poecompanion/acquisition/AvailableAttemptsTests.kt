package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.AccountContextId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AvailableAttemptsTests {

    @Test
    fun `zero attempts should be a valid recorded fact`() {
        val value = AvailableAttempts(AccountContextId.generate(), AcquisitionSourceId.generate(), 0)

        assertThat(value.count).isZero()
    }

    @Test
    fun `positive attempt counts should be valid`() {
        val value = AvailableAttempts(AccountContextId.generate(), AcquisitionSourceId.generate(), Int.MAX_VALUE)

        assertThat(value.count).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `negative attempt counts should be rejected`() {
        assertThatThrownBy {
            AvailableAttempts(AccountContextId.generate(), AcquisitionSourceId.generate(), -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
