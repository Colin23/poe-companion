package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BuildVariantRevisionTests {

    @Test
    fun `new revision should start as draft for its variant and compatibility version`() {
        val buildVariantId = BuildVariantId.generate()
        val compatibilityVersion = CompatibilityVersion(3, 30)
        val revision =
            BuildVariantRevision(
                id = BuildVariantRevisionId.generate(),
                buildVariantId = buildVariantId,
                compatibilityVersion = compatibilityVersion,
            )

        assertThat(revision.buildVariantId).isEqualTo(buildVariantId)
        assertThat(revision.compatibilityVersion).isEqualTo(compatibilityVersion)
        assertThat(revision.status).isEqualTo(BuildVariantRevisionStatus.DRAFT)
    }

    @Test
    fun `draft revision should activate`() {
        val revision = createRevision()

        revision.activate()

        assertThat(revision.status).isEqualTo(BuildVariantRevisionStatus.ACTIVE)
    }

    @Test
    fun `active revision should supersede`() {
        val revision = createRevision()
        revision.activate()

        revision.supersede()

        assertThat(revision.status).isEqualTo(BuildVariantRevisionStatus.SUPERSEDED)
    }

    @Test
    fun `draft revision should not supersede`() {
        val revision = createRevision()

        assertThatThrownBy(revision::supersede).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `active revision should not activate again`() {
        val revision = createRevision()
        revision.activate()

        assertThatThrownBy(revision::activate).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `superseded revision should not activate or supersede again`() {
        val revision = createRevision()
        revision.activate()
        revision.supersede()

        assertThatThrownBy(revision::activate).isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy(revision::supersede).isInstanceOf(IllegalStateException::class.java)
    }

    private fun createRevision(): BuildVariantRevision =
        BuildVariantRevision(
            id = BuildVariantRevisionId.generate(),
            buildVariantId = BuildVariantId.generate(),
            compatibilityVersion = CompatibilityVersion(3, 30),
        )
}
