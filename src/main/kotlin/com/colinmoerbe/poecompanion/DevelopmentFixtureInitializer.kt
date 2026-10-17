package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.BossDefinition
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionService
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.DropEstimateProvenance
import com.colinmoerbe.poecompanion.acquisition.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.FixedBernoulliModel
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimateService
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimateSnapshot
import com.colinmoerbe.poecompanion.build.BuildArchetype
import com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess
import com.colinmoerbe.poecompanion.build.BuildVariant
import com.colinmoerbe.poecompanion.build.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionStatus
import com.colinmoerbe.poecompanion.build.Requirement
import com.colinmoerbe.poecompanion.build.RequirementGroup
import com.colinmoerbe.poecompanion.build.RequirementImportance
import com.colinmoerbe.poecompanion.build.RequirementLogic
import com.colinmoerbe.poecompanion.catalog.DevelopmentCatalogAccess
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Creates the canonical A/B/C browser-demo scenario from real persisted domain objects, exactly once.
 *
 * The bean has no startup side effects of its own. Only [DevelopmentFixtureConfiguration] invokes it on startup,
 * while integration tests invoke it explicitly. Module-owned development access points keep persistence private
 * to each module; this orchestrator uses exposed root-package APIs without crossing persistence boundaries.
 */
@Component
@Suppress("LongParameterList")
internal class DevelopmentFixtureInitializer(
    private val uniques: DevelopmentCatalogAccess,
    private val leagueAccess: DevelopmentLeagueAccess,
    private val accountEvaluation: AccountEvaluationService,
    private val ownership: ManualOwnershipService,
    private val buildAccess: DevelopmentBuildAccess,
    private val bosses: BossDefinitionService,
    private val acquisitionAccess: DevelopmentAcquisitionAccess,
    private val encounters: BossEncounterKnowledgeService,
    private val drops: DropRelationshipKnowledgeService,
    private val estimates: FixedDropEstimateService,
    private val attempts: AvailableAttemptsService,
) {
    private val ids = DevelopmentFixtureIds
    private val compatibilityVersion = CompatibilityVersion(COMPATIBILITY_MAJOR, COMPATIBILITY_MINOR)
    private val scope = AcquisitionKnowledgeScope(compatibilityVersion, Ruleset.NORMAL)

    /** Creates an atomic fixture or verifies an existing fixture without resetting player-editable state. */
    @Transactional
    fun initialize() {
        if (uniques.findById(ids.uniqueA) != null) {
            verifyExistingFixture()
        } else {
            check(noOtherFixtureIdentitiesExist()) {
                "Partial development fixture detected; do not overwrite it. Restore or remove the synthetic data."
            }
            createCatalogAndAccount()
            createBuild()
            createAcquisition()
        }

        // Respect any already-selected account; this is never a reset operation.
        if (accountEvaluation.getSelected() == null) {
            accountEvaluation.select(ids.account, compatibilityVersion)
        }
    }

    private fun createCatalogAndAccount() {
        listOf(ids.uniqueA, ids.uniqueB, ids.uniqueC).forEach { uniques.save(UniqueDefinition(it)) }
        leagueAccess.saveLeague(
            LeagueDefinition(
                id = ids.league,
                name = "Development SSF league",
                type = LeagueType.CHALLENGE,
                participation = LeagueParticipation.SSF,
                mortality = LeagueMortality.SOFTCORE,
                realm = GameRealm.PC,
            ),
        )
        leagueAccess.saveAccount(AccountContext(ids.account, ids.league, Ruleset.NORMAL))
        ownership.setOwnedQuantity(ids.account, ids.uniqueA, 1)
        ownership.setOwnedQuantity(ids.account, ids.uniqueB, 1)
        // C=0 is represented by an absent ownership row.
    }

    private fun createBuild() {
        val revision = BuildVariantRevision(ids.revision, ids.variant, compatibilityVersion)
        revision.updateRequirementGroups(requirementGroups())
        buildAccess.createAndActivate(
            BuildArchetype(ids.archetype, "Build X"),
            BuildVariant(ids.variant, ids.archetype),
            revision,
        )
    }

    private fun createAcquisition() {
        bosses.create(BossDefinition(ids.boss, "Boss Y"))
        acquisitionAccess.saveSource(AcquisitionSource(ids.source))
        encounters.recordSnapshot(ids.source, scope, encounterSnapshot(), null)
        acquisitionAccess.saveRelationship(DropRelationship(ids.relationship, ids.source, ids.uniqueC))
        drops.recordSnapshot(
            ids.relationship,
            scope,
            DropRelationshipSnapshot(null, DropRelationshipState.CAN_DROP),
            null,
        )
        estimates.publish(ids.relationship, scope, estimateSnapshot(), null)
        attempts.setAvailableAttempts(ids.account, ids.source, INITIAL_ATTEMPTS)
    }

    private fun verifyExistingFixture() {
        check(uniques.findById(ids.uniqueB) != null && uniques.findById(ids.uniqueC) != null) {
            "Development fixture is missing Unique B or C"
        }
        val league = leagueAccess.findLeagueById(ids.league)
        check(
            league?.name == "Development SSF league" && league.type == LeagueType.CHALLENGE &&
                league.participation == LeagueParticipation.SSF && league.mortality == LeagueMortality.SOFTCORE &&
                league.realm == GameRealm.PC,
        ) { "Development fixture league definition was modified or removed" }

        val account = leagueAccess.findAccountById(ids.account)
        check(account?.leagueDefinitionId == ids.league && account.ruleset == Ruleset.NORMAL) {
            "Development fixture AccountContext was modified or removed"
        }
        check(buildAccess.findArchetypeById(ids.archetype)?.name == "Build X") {
            "Development fixture Build X archetype was modified or removed"
        }
        val variant = buildAccess.findVariantById(ids.variant)
        check(variant?.buildArchetypeId == ids.archetype && variant.label == null) {
            "Development fixture Build X variant was modified or removed"
        }
        verifyExistingBuild()
        verifyExistingAcquisition()
        // Ownership and AvailableAttempts are intentionally not validated or reset: they are player-editable.
    }

    private fun verifyExistingBuild() {
        val revision = buildAccess.findRevisionById(ids.revision)
        check(
            revision?.buildVariantId == ids.variant &&
                revision.compatibilityVersion == compatibilityVersion &&
                revision.status == BuildVariantRevisionStatus.ACTIVE &&
                revision.requirementGroups.map { Triple(it.importance, it.logic, it.requirements) } ==
                requirementGroups().map { Triple(it.importance, it.logic, it.requirements) },
        ) { "Development fixture active Build X revision was modified or removed" }
    }

    private fun verifyExistingAcquisition() {
        check(bosses.findById(ids.boss)?.displayName == "Boss Y") {
            "Development fixture Boss Y definition was modified or removed"
        }
        check(acquisitionAccess.findSourceById(ids.source) != null) { "Development fixture source is missing" }

        val relationship = acquisitionAccess.findRelationshipById(ids.relationship)
        check(
            relationship?.id == ids.relationship &&
                relationship.acquisitionSourceId == ids.source &&
                relationship.uniqueDefinitionId == ids.uniqueC,
        ) {
            "Development fixture source-to-C relationship was modified or removed"
        }
        check(encounters.findTimeline(ids.source, scope)?.snapshots == listOf(encounterSnapshot())) {
            "Development fixture encounter knowledge was modified or removed"
        }
        check(
            drops.findTimeline(ids.relationship, scope)?.snapshots ==
                listOf(DropRelationshipSnapshot(null, DropRelationshipState.CAN_DROP)),
        ) { "Development fixture drop applicability was modified or removed" }

        val probability = estimates.findAcceptedTimeline(ids.relationship, scope)
        check(
            probability?.snapshots == listOf(estimateSnapshot()) &&
                probability?.withdrawals?.isEmpty() == true,
        ) {
            "Development fixture accepted probability was modified or withdrawn"
        }
    }

    private fun noOtherFixtureIdentitiesExist(): Boolean = uniques.findById(ids.uniqueB) == null &&
        uniques.findById(ids.uniqueC) == null &&
        leagueAccess.findLeagueById(ids.league) == null &&
        leagueAccess.findAccountById(ids.account) == null &&
        buildAccess.findArchetypeById(ids.archetype) == null &&
        buildAccess.findVariantById(ids.variant) == null &&
        buildAccess.findRevisionById(ids.revision) == null &&
        bosses.findById(ids.boss) == null &&
        acquisitionAccess.findSourceById(ids.source) == null &&
        acquisitionAccess.findRelationshipById(ids.relationship) == null

    private fun requirementGroups(): List<RequirementGroup> = listOf(
        RequirementGroup(
            RequirementImportance.ENABLING,
            RequirementLogic.ALL,
            listOf(Requirement(ids.uniqueA, 1)),
        ),
        RequirementGroup(
            RequirementImportance.CORE,
            RequirementLogic.ALL,
            listOf(Requirement(ids.uniqueB, 1), Requirement(ids.uniqueC, 1)),
        ),
    )

    private fun encounterSnapshot(): BossEncounterKnowledgeSnapshot = BossEncounterKnowledgeSnapshot(
        effectiveFromPatch = null,
        displayLabel = "Boss Y",
        availability = AcquisitionSourceAvailability.AVAILABLE,
        bossParticipation = BossParticipation.Fixed(setOf(ids.boss)),
    )

    private fun estimateSnapshot(): FixedDropEstimateSnapshot = FixedDropEstimateSnapshot(
        id = ids.estimate,
        effectiveFromPatch = null,
        model = FixedBernoulliModel(FIXED_PROBABILITY),
        provenance = DropEstimateProvenance.MEASURED,
        evidenceReference = "synthetic-development-fixture:boss-y-to-unique-c",
        reviewedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )
    private companion object {
        const val COMPATIBILITY_MAJOR = 3
        const val COMPATIBILITY_MINOR = 30
        const val INITIAL_ATTEMPTS = 12
        const val FIXED_PROBABILITY = 0.10
    }
}
