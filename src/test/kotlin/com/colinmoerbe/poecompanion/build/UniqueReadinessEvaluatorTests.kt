package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.account.CurrentOwnership
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UniqueReadinessEvaluatorTests {

    private val evaluator = UniqueReadinessEvaluator()

    @Test
    fun `requirements should compare owned quantity with required quantity and retain explanation`() {
        val below = UniqueDefinitionId.generate()
        val exact = UniqueDefinitionId.generate()
        val above = UniqueDefinitionId.generate()
        val absent = UniqueDefinitionId.generate()
        val group =
            group(
                importance = RequirementImportance.CORE,
                logic = RequirementLogic.ALL,
                Requirement(below, 2),
                Requirement(exact, 2),
                Requirement(above, 2),
                Requirement(absent, 2),
            )
        val revision = activeRevision(group)
        val ownership =
            ownership(
                below to 1,
                exact to 2,
                above to 3,
            )

        val result = evaluator.evaluate(revision, ownership)

        assertThat(result.readiness).isEqualTo(UniqueReadiness.PARTIAL)
        val requirements = result.groups.single().requirements
        assertThat(requirements.map { it.ownedQuantity }).containsExactly(1, 2, 3, 0)
        assertThat(requirements.map { it.satisfied }).containsExactly(false, true, true, false)
        assertThat(result.groups.single().satisfied).isFalse()
    }

    @Test
    fun `any group should be satisfied when one alternative is satisfied`() {
        val first = UniqueDefinitionId.generate()
        val second = UniqueDefinitionId.generate()
        val revision =
            activeRevision(
                group(
                    importance = RequirementImportance.CORE,
                    logic = RequirementLogic.ANY,
                    Requirement(first, 2),
                    Requirement(second, 1),
                ),
            )

        val result = evaluator.evaluate(revision, ownership(second to 1))

        assertThat(result.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        assertThat(result.groups.single().satisfied).isTrue()
    }

    @Test
    fun `any group should be unsatisfied when no alternative is satisfied`() {
        val revision =
            activeRevision(
                group(
                    importance = RequirementImportance.CORE,
                    logic = RequirementLogic.ANY,
                    Requirement(UniqueDefinitionId.generate(), 1),
                    Requirement(UniqueDefinitionId.generate(), 1),
                ),
            )

        val result = evaluator.evaluate(revision, ownership())

        assertThat(result.readiness).isEqualTo(UniqueReadiness.PARTIAL)
        assertThat(result.groups.single().satisfied).isFalse()
    }

    @Test
    fun `unsatisfied enabling group should block even when core is also unsatisfied`() {
        val revision =
            activeRevision(
                group(
                    RequirementImportance.ENABLING,
                    RequirementLogic.ALL,
                    Requirement(UniqueDefinitionId.generate(), 1),
                ),
                group(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    Requirement(UniqueDefinitionId.generate(), 1),
                ),
            )

        val result = evaluator.evaluate(revision, ownership())

        assertThat(result.readiness).isEqualTo(UniqueReadiness.BLOCKED)
    }

    @Test
    fun `satisfied enabling with unsatisfied core should be partial`() {
        val enabling = UniqueDefinitionId.generate()
        val revision =
            activeRevision(
                group(
                    RequirementImportance.ENABLING,
                    RequirementLogic.ALL,
                    Requirement(enabling, 1),
                ),
                group(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    Requirement(UniqueDefinitionId.generate(), 1),
                ),
            )

        val result = evaluator.evaluate(revision, ownership(enabling to 1))

        assertThat(result.readiness).isEqualTo(UniqueReadiness.PARTIAL)
    }

    @Test
    fun `satisfied enabling and core groups should be unique ready`() {
        val enabling = UniqueDefinitionId.generate()
        val core = UniqueDefinitionId.generate()
        val revision =
            activeRevision(
                group(
                    RequirementImportance.ENABLING,
                    RequirementLogic.ALL,
                    Requirement(enabling, 1),
                ),
                group(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    Requirement(core, 1),
                ),
            )

        val result =
            evaluator.evaluate(
                revision,
                ownership(
                    enabling to 1,
                    core to 1,
                ),
            )

        assertThat(result.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        assertThat(result.groups).allMatch { it.satisfied }
        assertThat(result.hasRequiredUniqueGroups).isTrue()
    }

    @Test
    fun `unsatisfied upgrade should not affect primary readiness`() {
        val core = UniqueDefinitionId.generate()
        val revision =
            activeRevision(
                group(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    Requirement(core, 1),
                ),
                group(
                    RequirementImportance.UPGRADE,
                    RequirementLogic.ALL,
                    Requirement(UniqueDefinitionId.generate(), 1),
                ),
            )

        val result = evaluator.evaluate(revision, ownership(core to 1))

        assertThat(result.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        assertThat(result.groups.single { it.group.importance == RequirementImportance.UPGRADE }.satisfied).isFalse()
    }

    @Test
    fun `revision with no groups should be unique ready with no required unique groups`() {
        val result = evaluator.evaluate(activeRevision(), ownership())

        assertThat(result.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        assertThat(result.groups).isEmpty()
        assertThat(result.hasRequiredUniqueGroups).isFalse()
    }

    @Test
    fun `revision with only upgrades should be unique ready with no required unique groups`() {
        val revision =
            activeRevision(
                group(
                    RequirementImportance.UPGRADE,
                    RequirementLogic.ALL,
                    Requirement(UniqueDefinitionId.generate(), 1),
                ),
            )

        val result = evaluator.evaluate(revision, ownership())

        assertThat(result.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        assertThat(result.groups.single().satisfied).isFalse()
        assertThat(result.hasRequiredUniqueGroups).isFalse()
    }

    @Test
    fun `same owned unique should satisfy independent groups without consumption`() {
        val uniqueDefinitionId = UniqueDefinitionId.generate()
        val revision =
            activeRevision(
                group(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    Requirement(uniqueDefinitionId, 1),
                ),
                group(
                    RequirementImportance.CORE,
                    RequirementLogic.ANY,
                    Requirement(uniqueDefinitionId, 1),
                    Requirement(UniqueDefinitionId.generate(), 1),
                ),
            )

        val result = evaluator.evaluate(revision, ownership(uniqueDefinitionId to 1))

        assertThat(result.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        assertThat(result.groups).allMatch { it.satisfied }
    }

    @Test
    fun `draft and superseded revisions should not be evaluated`() {
        val draft = revision()
        val superseded = revision()
        superseded.activate()
        superseded.supersede()

        assertThatThrownBy { evaluator.evaluate(draft, ownership()) }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { evaluator.evaluate(superseded, ownership()) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `evaluation result collections should not allow mutation`() {
        val revision =
            activeRevision(
                group(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    Requirement(UniqueDefinitionId.generate(), 1),
                ),
            )
        val result = evaluator.evaluate(revision, ownership())

        @Suppress("UNCHECKED_CAST")
        val exposedGroups = result.groups as MutableList<RequirementGroupEvaluation>

        @Suppress("UNCHECKED_CAST")
        val exposedRequirements =
            result.groups.single().requirements as MutableList<RequirementEvaluation>

        assertThatThrownBy { exposedGroups.clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { exposedRequirements.clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    private fun activeRevision(vararg groups: RequirementGroup): BuildVariantRevision = revision().also {
        it.updateRequirementGroups(groups.toList())
        it.activate()
    }

    private fun revision(): BuildVariantRevision = BuildVariantRevision(
        id = BuildVariantRevisionId.generate(),
        buildVariantId = BuildVariantId.generate(),
        compatibilityVersion = CompatibilityVersion(3, 30),
    )

    private fun group(
        importance: RequirementImportance,
        logic: RequirementLogic,
        vararg requirements: Requirement,
    ): RequirementGroup = RequirementGroup(
        importance = importance,
        logic = logic,
        requirements = requirements.toList(),
    )

    private fun ownership(vararg quantities: Pair<UniqueDefinitionId, Int>): CurrentOwnership = CurrentOwnership(
        accountContextId = AccountContextId.generate(),
        quantities = quantities.toMap(),
    )
}
