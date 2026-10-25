package com.colinmoerbe.poecompanion.browser.builds

import com.colinmoerbe.poecompanion.browser.shared.UniqueDisplayLabels
import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.readiness.RequirementGroupEvaluation
import com.colinmoerbe.poecompanion.build.readiness.UniqueReadiness
import com.colinmoerbe.poecompanion.build.readmodel.BuildCatalogQueryService
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessQueryResult
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessService
import com.colinmoerbe.poecompanion.build.requirements.RequirementGroup
import com.colinmoerbe.poecompanion.build.requirements.RequirementImportance
import com.colinmoerbe.poecompanion.catalog.UniqueCatalogQueryService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/** Browser-facing composition across Build and Catalog, without cross-module persistence access. */
@Service
internal class BuildBrowserReadModelService(
    private val builds: BuildCatalogQueryService,
    private val readiness: BuildReadinessService,
    private val catalog: UniqueCatalogQueryService,
    private val gameSelection: GameEnvironmentSelectionService,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun list(): BuildBrowserListPage {
        val knownBuilds = builds.findAll()
        val evaluated = readiness.evaluateAll(knownBuilds.map { it.variant.id })
        val entries = knownBuilds.map { build ->
            val result = evaluated.getValue(build.variant.id)
            val state = stateOf(result)
            BuildBrowserListEntry(
                id = build.variant.id.value,
                displayName = build.displayName,
                state = state,
                category = categoryOf(result, state),
            )
        }.sortedWith(compareBy<BuildBrowserListEntry> { it.displayName.lowercase() }.thenBy { it.id })

        return BuildBrowserListPage(
            totalCount = entries.size,
            sections = BuildBrowserListCategory.entries.mapNotNull { category ->
                val matching = entries.filter { it.category == category }
                if (matching.isEmpty()) null else BuildBrowserSection(category, matching)
            },
            environmentSelected = gameSelection.getSelected() != null,
        )
    }

    @Suppress("LongMethod")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun detail(id: BuildVariantId): BuildBrowserDetailPage? {
        val build = builds.findById(id) ?: return null
        val result = readiness.evaluate(id)
        val state = stateOf(result)

        return when (result) {
            is BuildReadinessQueryResult.Evaluated -> {
                val catalogIds = catalog.findAll().mapTo(hashSetOf()) { it.id }
                BuildBrowserDetailPage(
                    id = id.value,
                    displayName = build.displayName,
                    state = state,
                    explanation = if (!result.readiness.hasRequiredUniqueGroups) {
                        "This Build has no mandatory Unique requirements. Optional upgrades do not affect readiness."
                    } else {
                        state.explanation
                    },
                    compatibilityVersion = result.revision.compatibilityVersion.let { "${it.major}.${it.minor}" },
                    groups = result.readiness.groups.map { group -> groupToView(group, catalogIds) },
                    hasRequiredUniqueGroups = result.readiness.hasRequiredUniqueGroups,
                )
            }

            is BuildReadinessQueryResult.PersonalOwnershipUnavailable -> {
                val catalogIds = catalog.findAll().mapTo(hashSetOf()) { it.id }
                val hasRequired = result.revision.requirementGroups.any {
                    it.importance != RequirementImportance.UPGRADE
                }
                BuildBrowserDetailPage(
                    id = id.value,
                    displayName = build.displayName,
                    state = state,
                    explanation = if (hasRequired) {
                        "Requirements are known, but personal Unique ownership has not been recorded for this environment."
                    } else {
                        "This Build has no mandatory Unique requirements; personal ownership is not evaluated."
                    },
                    compatibilityVersion = result.evaluationContext.compatibilityVersion.toString(),
                    groups = result.revision.requirementGroups.map { group -> groupToView(group, catalogIds) },
                    hasRequiredUniqueGroups = hasRequired,
                )
            }

            is BuildReadinessQueryResult.NoCompatibleActiveRevision -> BuildBrowserDetailPage(
                id = id.value,
                displayName = build.displayName,
                state = state,
                explanation = state.explanation,
                compatibilityVersion = result.evaluationContext.compatibilityVersion.let {
                    "${it.major}.${it.minor}"
                },
                groups = emptyList(),
                hasRequiredUniqueGroups = null,
            )

            BuildReadinessQueryResult.NoAccountEvaluationSelected -> BuildBrowserDetailPage(
                id = id.value,
                displayName = build.displayName,
                state = state,
                explanation = state.explanation,
                compatibilityVersion = null,
                groups = emptyList(),
                hasRequiredUniqueGroups = null,
            )
        }
    }

    private fun groupToView(group: RequirementGroup, catalogIds: Set<UniqueDefinitionId>): BuildBrowserGroup =
        BuildBrowserGroup(
            importance = group.importance.name,
            logic = group.logic.name,
            satisfied = null,
            requirements = group.requirements.map { requirement ->
                val id = requirement.uniqueDefinitionId
                val label = UniqueDisplayLabels.forId(id)
                BuildBrowserRequirement(
                    id = id.value,
                    displayName = label.name,
                    syntheticLabel = label.synthetic,
                    catalogKnown = id in catalogIds,
                    ownedQuantity = null,
                    requiredQuantity = requirement.requiredQuantity,
                    satisfied = null,
                )
            },
        )

    private fun groupToView(
        evaluated: RequirementGroupEvaluation,
        catalogIds: Set<UniqueDefinitionId>,
    ): BuildBrowserGroup = BuildBrowserGroup(
        importance = evaluated.group.importance.name,
        logic = evaluated.group.logic.name,
        satisfied = evaluated.satisfied,
        requirements = evaluated.requirements.map { entry ->
            val id = entry.requirement.uniqueDefinitionId
            val label = UniqueDisplayLabels.forId(id)
            BuildBrowserRequirement(
                id = id.value,
                displayName = label.name,
                syntheticLabel = label.synthetic,
                catalogKnown = id in catalogIds,
                ownedQuantity = entry.ownedQuantity,
                requiredQuantity = entry.requirement.requiredQuantity,
                satisfied = entry.satisfied,
            )
        },
    )

    private fun categoryOf(result: BuildReadinessQueryResult, state: BuildBrowserState): BuildBrowserListCategory {
        val noRequired = when (result) {
            is BuildReadinessQueryResult.Evaluated -> !result.readiness.hasRequiredUniqueGroups

            is BuildReadinessQueryResult.PersonalOwnershipUnavailable ->
                result.revision.requirementGroups.none { it.importance != RequirementImportance.UPGRADE }

            else -> false
        }
        if (noRequired) return BuildBrowserListCategory.NO_REQUIRED

        return when (state) {
            BuildBrowserState.UNIQUE_READY -> BuildBrowserListCategory.UNIQUE_READY
            BuildBrowserState.PARTIAL -> BuildBrowserListCategory.PARTIAL
            BuildBrowserState.BLOCKED -> BuildBrowserListCategory.BLOCKED
            BuildBrowserState.NO_PERSONAL -> BuildBrowserListCategory.NO_PERSONAL
            BuildBrowserState.NO_ACTIVE_REVISION -> BuildBrowserListCategory.NO_ACTIVE_REVISION
            BuildBrowserState.NO_ACCOUNT -> BuildBrowserListCategory.NO_ACCOUNT
        }
    }

    private fun stateOf(result: BuildReadinessQueryResult): BuildBrowserState = when (result) {
        BuildReadinessQueryResult.NoAccountEvaluationSelected -> BuildBrowserState.NO_ACCOUNT

        is BuildReadinessQueryResult.NoCompatibleActiveRevision -> BuildBrowserState.NO_ACTIVE_REVISION

        is BuildReadinessQueryResult.PersonalOwnershipUnavailable -> BuildBrowserState.NO_PERSONAL

        is BuildReadinessQueryResult.Evaluated -> when (result.readiness.readiness) {
            UniqueReadiness.UNIQUE_READY -> BuildBrowserState.UNIQUE_READY
            UniqueReadiness.PARTIAL -> BuildBrowserState.PARTIAL
            UniqueReadiness.BLOCKED -> BuildBrowserState.BLOCKED
        }
    }
}
