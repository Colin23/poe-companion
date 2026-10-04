package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
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
        assertThat(revision.requirementGroups).isEmpty()
    }

    @Test
    fun `draft revision should update and replace requirement groups`() {
        val revision = createRevision()
        val first = createRequirementGroup()
        val second = createRequirementGroup()

        revision.updateRequirementGroups(listOf(first))
        assertThat(revision.requirementGroups).containsExactly(first)

        revision.updateRequirementGroups(listOf(second))
        assertThat(revision.requirementGroups).containsExactly(second)
    }

    @Test
    fun `draft revision should allow no requirement groups`() {
        val revision = createRevision()
        revision.updateRequirementGroups(listOf(createRequirementGroup()))

        revision.updateRequirementGroups(emptyList())

        assertThat(revision.requirementGroups).isEmpty()
    }

    @Test
    fun `active revision should reject requirement group changes`() {
        val revision = createRevision()
        revision.activate()

        assertThatThrownBy {
            revision.updateRequirementGroups(listOf(createRequirementGroup()))
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `superseded revision should reject requirement group changes`() {
        val revision = createRevision()
        revision.activate()
        revision.supersede()

        assertThatThrownBy {
            revision.updateRequirementGroups(listOf(createRequirementGroup()))
        }.isInstanceOf(IllegalStateException::class.java)
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

    private fun createRevision(): BuildVariantRevision = BuildVariantRevision(
        id = BuildVariantRevisionId.generate(),
        buildVariantId = BuildVariantId.generate(),
        compatibilityVersion = CompatibilityVersion(3, 30),
    )

    private fun createRequirementGroup(): RequirementGroup = RequirementGroup(
        listOf(Requirement(UniqueDefinitionId.generate(), 1)),
    )
}
