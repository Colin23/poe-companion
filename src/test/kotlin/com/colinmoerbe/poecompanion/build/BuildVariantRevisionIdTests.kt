package com.colinmoerbe.poecompanion.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class BuildVariantRevisionIdTests {

    @Test
    fun `revision identity should have value semantics`() {
        val value = UUID.randomUUID()

        assertThat(BuildVariantRevisionId(value)).isEqualTo(BuildVariantRevisionId(value))
    }

    @Test
    fun `generated revision identities should be distinct`() {
        assertThat(BuildVariantRevisionId.generate()).isNotEqualTo(BuildVariantRevisionId.generate())
    }
}
