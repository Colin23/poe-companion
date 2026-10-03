package com.colinmoerbe.poecompanion.build

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BuildArchetypeAndVariantTests {

    @Test
    fun `archetype should retain its human-readable name`() {
        val archetype = BuildArchetype(BuildArchetypeId.generate(), "RF Chieftain")

        assertThat(archetype.name).isEqualTo("RF Chieftain")
    }

    @Test
    fun `archetype should reject blank name`() {
        assertThatThrownBy {
            BuildArchetype(BuildArchetypeId.generate(), " ")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `variant should retain its archetype identity and optional label`() {
        val archetypeId = BuildArchetypeId.generate()
        val variant = BuildVariant(BuildVariantId.generate(), archetypeId, "Svalinn")

        assertThat(variant.buildArchetypeId).isEqualTo(archetypeId)
        assertThat(variant.label).isEqualTo("Svalinn")
    }

    @Test
    fun `variant should allow missing label`() {
        val variant = BuildVariant(BuildVariantId.generate(), BuildArchetypeId.generate())

        assertThat(variant.label).isNull()
    }

    @Test
    fun `variant should reject blank label`() {
        assertThatThrownBy {
            BuildVariant(BuildVariantId.generate(), BuildArchetypeId.generate(), " ")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
