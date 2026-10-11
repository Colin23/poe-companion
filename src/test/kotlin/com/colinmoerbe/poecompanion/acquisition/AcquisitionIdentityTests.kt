package com.colinmoerbe.poecompanion.acquisition

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AcquisitionIdentityTests {

    @Test
    fun `generated boss definition ids should be distinct and reconstructable`() {
        val original = BossDefinitionId.generate()
        val other = BossDefinitionId.generate()

        assertThat(other).isNotEqualTo(original)
        assertThat(BossDefinitionId(original.value)).isEqualTo(original)
    }

    @Test
    fun `boss definition should retain identity and display name`() {
        val id = BossDefinitionId.generate()

        val boss = BossDefinition(id, "The Eater of Worlds")

        assertThat(boss.id).isEqualTo(id)
        assertThat(boss.displayName).isEqualTo("The Eater of Worlds")
    }

    @Test
    fun `boss definition should reject blank display name`() {
        assertThatThrownBy {
            BossDefinition(BossDefinitionId.generate(), " ")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `generated acquisition source ids should be distinct and reconstructable`() {
        val original = AcquisitionSourceId.generate()
        val other = AcquisitionSourceId.generate()

        assertThat(other).isNotEqualTo(original)
        assertThat(AcquisitionSourceId(original.value)).isEqualTo(original)
    }

    @Test
    fun `acquisition source should retain stable identity without encounter metadata`() {
        val id = AcquisitionSourceId.generate()

        val source = AcquisitionSource(id)

        assertThat(source.id).isEqualTo(id)
    }
}
