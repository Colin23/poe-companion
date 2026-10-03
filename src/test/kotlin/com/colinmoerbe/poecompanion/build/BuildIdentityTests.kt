package com.colinmoerbe.poecompanion.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class BuildIdentityTests {

    @Test
    fun `archetype identity should have value semantics`() {
        val value = UUID.randomUUID()

        assertThat(BuildArchetypeId(value)).isEqualTo(BuildArchetypeId(value))
    }

    @Test
    fun `generated archetype identities should be distinct`() {
        assertThat(BuildArchetypeId.generate()).isNotEqualTo(BuildArchetypeId.generate())
    }

    @Test
    fun `variant identity should have value semantics`() {
        val value = UUID.randomUUID()

        assertThat(BuildVariantId(value)).isEqualTo(BuildVariantId(value))
    }

    @Test
    fun `generated variant identities should be distinct`() {
        assertThat(BuildVariantId.generate()).isNotEqualTo(BuildVariantId.generate())
    }
}
