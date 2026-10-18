package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.build.BuildCatalogQueryService
import com.colinmoerbe.poecompanion.build.BuildReadinessQueryResult
import com.colinmoerbe.poecompanion.build.BuildReadinessService
import com.colinmoerbe.poecompanion.build.BuildVariantId
import com.colinmoerbe.poecompanion.build.RequirementGroupEvaluation
import com.colinmoerbe.poecompanion.build.UniqueReadiness
import com.colinmoerbe.poecompanion.catalog.UniqueCatalogQueryService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/** Browser-facing composition across Build and Catalog, without cross-module persistence access. */
@Service
internal class BuildBrowserReadModelService(
    private val builds: BuildCatalogQueryService,
    private val readiness: BuildReadinessService,
    private val catalog: UniqueCatalogQueryService,
    private val accountEvaluation: AccountEvaluationService,
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
            accountSelected = accountEvaluation.getSelected() != null,
        )
    }

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

            is BuildReadinessQueryResult.NoCompatibleActiveRevision -> BuildBrowserDetailPage(
                id = id.value,
                displayName = build.displayName,
                state = state,
                explanation = state.explanation,
                compatibilityVersion = result.accountEvaluation.evaluationContext.compatibilityVersion.let {
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

        is BuildReadinessQueryResult.Evaluated -> when (result.readiness.readiness) {
            UniqueReadiness.UNIQUE_READY -> BuildBrowserState.UNIQUE_READY
            UniqueReadiness.PARTIAL -> BuildBrowserState.PARTIAL
            UniqueReadiness.BLOCKED -> BuildBrowserState.BLOCKED
        }
    }
}
