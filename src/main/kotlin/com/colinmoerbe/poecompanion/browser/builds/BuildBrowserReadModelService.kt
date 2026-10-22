package com.colinmoerbe.poecompanion.browser.builds

import com.colinmoerbe.poecompanion.browser.shared.UniqueDisplayLabels
import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.readiness.RequirementGroupEvaluation
import com.colinmoerbe.poecompanion.build.readiness.UniqueReadiness
import com.colinmoerbe.poecompanion.build.requirements.RequirementGroup
import com.colinmoerbe.poecompanion.build.requirements.RequirementImportance
import com.colinmoerbe.poecompanion.build.readmodel.BuildCatalogQueryService
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessQueryResult
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessService
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
            val explanation = when {
                result is BuildReadinessQueryResult.Evaluated && !result.readiness.hasRequiredUniqueGroups ->
                    "No required Uniques"
                result is BuildReadinessQueryResult.PersonalOwnershipUnavailable &&
                    result.revision.requirementGroups.none { it.importance != RequirementImportance.UPGRADE } ->
                    "No required Uniques"

                else -> state.explanation
            }
            BuildBrowserListEntry(
                id = build.variant.id.value,
                displayName = build.displayName,
                state = state,
                explanation = explanation,
            )
        }.sortedWith(compareBy<BuildBrowserListEntry> { it.displayName.lowercase() }.thenBy { it.id })

        return BuildBrowserListPage(
            totalCount = entries.size,
            sections = BuildBrowserState.entries.mapNotNull { state ->
                val matching = entries.filter { it.state == state }
                if (matching.isEmpty()) null else BuildBrowserSection(state, matching)
            },
            accountSelected = gameSelection.getSelected() != null,
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
                        "No required Uniques"
                    } else {
                        state.explanation
                    },
                    compatibilityVersion = result.revision.compatibilityVersion.let { "${it.major}.${it.minor}" },
                    revisionStatus = "Active revision",
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
                    explanation = if (hasRequired) state.explanation else "No required Uniques",
                    compatibilityVersion = result.evaluationContext.compatibilityVersion.toString(),
                    revisionStatus = "Active revision",
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
                revisionStatus = "No compatible active revision",
                groups = emptyList(),
                hasRequiredUniqueGroups = null,
            )

            BuildReadinessQueryResult.NoAccountEvaluationSelected -> BuildBrowserDetailPage(
                id = id.value,
                displayName = build.displayName,
                state = state,
                explanation = state.explanation,
                compatibilityVersion = null,
                revisionStatus = "Readiness not evaluated",
                groups = emptyList(),
                hasRequiredUniqueGroups = null,
            )
        }
    }

    private fun groupToView(
        group: RequirementGroup,
        catalogIds: Set<UniqueDefinitionId>,
    ): BuildBrowserGroup = BuildBrowserGroup(
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
