package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.encounter.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.probability.DropEstimateId
import com.colinmoerbe.poecompanion.acquisition.probability.DropEstimateProvenance
import com.colinmoerbe.poecompanion.acquisition.probability.FixedBernoulliModel
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateService
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateSnapshot
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinition
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionService
import com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess
import com.colinmoerbe.poecompanion.build.identity.BuildArchetype
import com.colinmoerbe.poecompanion.build.identity.BuildArchetypeId
import com.colinmoerbe.poecompanion.build.identity.BuildVariant
import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.requirements.Requirement
import com.colinmoerbe.poecompanion.build.requirements.RequirementGroup
import com.colinmoerbe.poecompanion.build.requirements.RequirementImportance
import com.colinmoerbe.poecompanion.build.requirements.RequirementLogic
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionStatus
import com.colinmoerbe.poecompanion.catalog.DevelopmentCatalogAccess
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import com.colinmoerbe.poecompanion.security.identity.DevelopmentFixtureActor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Additive synthetic game knowledge and independent USER/ADMIN personal records.
 *
 * Runs inside the legacy initializer's transaction; when present, canonical records are verified
 * but editable personal quantities, attempts and selections are never repaired or reset.
 */
@Component
@ConditionalOnProperty(prefix = "poe-companion.dev-fixture", name = ["enabled"], havingValue = "true")
@Suppress("LongParameterList")
internal class ExpandedDevelopmentFixture(
    private val uniques: DevelopmentCatalogAccess,
    private val league: DevelopmentLeagueAccess,
    private val selection: AccountEvaluationService,
    private val ownership: ManualOwnershipService,
    private val builds: DevelopmentBuildAccess,
    private val bosses: BossDefinitionService,
    private val acquisition: DevelopmentAcquisitionAccess,
    private val encounters: BossEncounterKnowledgeService,
    private val relationships: DropRelationshipKnowledgeService,
    private val estimates: FixedDropEstimateService,
    private val attempts: AvailableAttemptsService,
    private val actor: DevelopmentFixtureActor,
) {
    private val ids = DevelopmentFixtureIds
    private val definitions = ExpandedFixtureDefinitions
    private val version = CompatibilityVersion(3, 30)
    private val scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)
    private val newUniques = listOf(
        ids.uniqueD, ids.uniqueE, ids.uniqueF, ids.uniqueG, ids.uniqueH,
        ids.uniqueI, ids.uniqueJ, ids.uniqueK, ids.uniqueL,
    )

    /** Detect any existing expansion identity, including partial expansions missing the first Unique. */
    fun hasAnyIdentities(): Boolean = when {
        newUniques.any { uniques.findById(it) != null } -> true
        league.findLeagueById(ids.standardLeague) != null -> true
        listOf(ids.adminChallengeAccount, ids.adminStandardAccount).any { league.findAccountById(it) != null } -> true

        listOf(ids.archetypeP, ids.archetypeQ, ids.archetypeR).any { builds.findArchetypeById(it) != null } -> true
        listOf(ids.variantP, ids.variantQ, ids.variantR).any { builds.findVariantById(it) != null } -> true
        listOf(ids.revisionP, ids.revisionQ, ids.revisionR).any { builds.findRevisionById(it) != null } -> true

        listOf(ids.bossP, ids.bossQ, ids.bossR).any { bosses.findById(it) != null } -> true
        listOf(ids.sourceP, ids.sourceQ, ids.sourceR).any { acquisition.findSourceById(it) != null } -> true
        listOf(ids.relationshipPD, ids.relationshipPE, ids.relationshipQH, ids.relationshipRK)
            .any { acquisition.findRelationshipById(it) != null } -> true

        else -> false
    }

    fun create() {
        actor.asAdmin {
            newUniques.forEach { uniques.save(UniqueDefinition(it)) }
            league.saveLeague(definitions.standardLeague())
            createBuild(ids.archetypeP, ids.variantP, ids.revisionP, "Build P", definitions.buildPRequirements())
            createBuild(ids.archetypeQ, ids.variantQ, ids.revisionQ, "Build Q", definitions.buildQRequirements())
            createBuild(ids.archetypeR, ids.variantR, ids.revisionR, "Build R", definitions.buildRRequirements())
            createSource(ids.bossP, ids.sourceP, "Boss P", AcquisitionSourceAvailability.AVAILABLE)
            createSource(ids.bossQ, ids.sourceQ, "Boss Q", AcquisitionSourceAvailability.AVAILABLE)
            createSource(ids.bossR, ids.sourceR, "Boss R", AcquisitionSourceAvailability.UNAVAILABLE)
            createRelationship(ids.relationshipPD, ids.sourceP, ids.uniqueD, DropRelationshipState.CAN_DROP)
            createRelationship(ids.relationshipPE, ids.sourceP, ids.uniqueE, DropRelationshipState.CANNOT_DROP)
            createRelationship(ids.relationshipQH, ids.sourceQ, ids.uniqueH, DropRelationshipState.CAN_DROP)
            createRelationship(ids.relationshipRK, ids.sourceR, ids.uniqueK, DropRelationshipState.CAN_DROP)
            publish(ids.relationshipPD, ids.estimateP, BOSS_P_CHANCE, "boss-p-to-unique-d")
            publish(ids.relationshipRK, ids.estimateR, BOSS_R_CHANCE, "boss-r-to-unique-k")
        }

        actor.asLegacyUser {
            ownership.setOwnedQuantity(ids.account, ids.uniqueH, 1)
        }
        actor.asAdmin {
            league.saveAccount(
                AccountContext(ids.adminChallengeAccount, ids.league, Ruleset.NORMAL, ConfiguredApplicationUsers.ADMIN),
            )
            league.saveAccount(
                AccountContext(
                    ids.adminStandardAccount,
                    ids.standardLeague,
                    Ruleset.NORMAL,
                    ConfiguredApplicationUsers.ADMIN,
                ),
            )
            listOf(ids.uniqueA, ids.uniqueB, ids.uniqueC, ids.uniqueD, ids.uniqueF).forEach {
                ownership.setOwnedQuantity(ids.adminChallengeAccount, it, 1)
            }
            ownership.setOwnedQuantity(ids.adminChallengeAccount, ids.uniqueH, 2)

            listOf(ids.uniqueB, ids.uniqueC, ids.uniqueE, ids.uniqueF, ids.uniqueG, ids.uniqueH).forEach {
                ownership.setOwnedQuantity(ids.adminStandardAccount, it, 1)
            }

            attempts.setAvailableAttempts(ids.adminChallengeAccount, ids.source, 0)
            attempts.setAvailableAttempts(ids.adminChallengeAccount, ids.sourceP, ADMIN_CHALLENGE_P_ATTEMPTS)
            attempts.setAvailableAttempts(ids.adminStandardAccount, ids.sourceP, 0)
            attempts.setAvailableAttempts(ids.adminStandardAccount, ids.sourceQ, 2)
            // First-time default only: a later cleared or changed selection must remain untouched.
            selection.select(ids.adminChallengeAccount, version)
        }
    }

    fun verify() {
        check(newUniques.all { uniques.findById(it) != null }) {
            "Partial expanded development fixture: missing Unique definitions"
        }
        val standardLeague = league.findLeagueById(ids.standardLeague)
        check(
            standardLeague?.name == "Development Standard SSF" &&
                standardLeague.type == LeagueType.STANDARD &&
                standardLeague.participation == LeagueParticipation.SSF &&
                standardLeague.mortality == LeagueMortality.SOFTCORE &&
                standardLeague.realm == GameRealm.PC,
        ) { "Partial expanded development fixture: Standard league metadata" }

        val adminContextsValid = listOf(
            ids.adminChallengeAccount to ids.league,
            ids.adminStandardAccount to ids.standardLeague,
        ).all { (accountId, leagueId) ->
            league.findAccountById(accountId)?.let { account ->
                account.leagueDefinitionId == leagueId && account.ruleset == Ruleset.NORMAL &&
                    account.ownerApplicationUserId == ConfiguredApplicationUsers.ADMIN
            } == true
        }
        check(adminContextsValid) {
            "Partial expanded development fixture: ADMIN AccountContext ownership or league"
        }

        verifyBuild(ids.archetypeP, ids.variantP, ids.revisionP, "Build P", definitions.buildPRequirements())
        verifyBuild(ids.archetypeQ, ids.variantQ, ids.revisionQ, "Build Q", definitions.buildQRequirements())
        verifyBuild(ids.archetypeR, ids.variantR, ids.revisionR, "Build R", definitions.buildRRequirements())
        verifySource(ids.bossP, ids.sourceP, "Boss P", AcquisitionSourceAvailability.AVAILABLE)
        verifySource(ids.bossQ, ids.sourceQ, "Boss Q", AcquisitionSourceAvailability.AVAILABLE)
        verifySource(ids.bossR, ids.sourceR, "Boss R", AcquisitionSourceAvailability.UNAVAILABLE)
        verifyRelationship(ids.relationshipPD, ids.sourceP, ids.uniqueD, DropRelationshipState.CAN_DROP)
        verifyRelationship(ids.relationshipPE, ids.sourceP, ids.uniqueE, DropRelationshipState.CANNOT_DROP)
        verifyRelationship(ids.relationshipQH, ids.sourceQ, ids.uniqueH, DropRelationshipState.CAN_DROP)
        verifyRelationship(ids.relationshipRK, ids.sourceR, ids.uniqueK, DropRelationshipState.CAN_DROP)
        verifyEstimate(ids.relationshipPD, ids.estimateP, BOSS_P_CHANCE, "boss-p-to-unique-d")
        verifyEstimate(ids.relationshipRK, ids.estimateR, BOSS_R_CHANCE, "boss-r-to-unique-k")
        check(
            estimates.findAcceptedTimeline(ids.relationshipPE, scope) == null &&
                estimates.findAcceptedTimeline(ids.relationshipQH, scope) == null,
        ) { "Expanded development fixture contains unexpected fixed estimates" }
        // Ownership, saved attempts and personal selection are intentionally not checked.
    }

    private fun createBuild(
        archetypeId: BuildArchetypeId,
        variantId: BuildVariantId,
        revisionId: BuildVariantRevisionId,
        name: String,
        groups: List<RequirementGroup>,
    ) {
        val revision = BuildVariantRevision(revisionId, variantId, version)
        revision.updateRequirementGroups(groups)
        builds.createAndActivate(BuildArchetype(archetypeId, name), BuildVariant(variantId, archetypeId), revision)
    }

    private fun verifyBuild(
        archetypeId: BuildArchetypeId,
        variantId: BuildVariantId,
        revisionId: BuildVariantRevisionId,
        name: String,
        groups: List<RequirementGroup>,
    ) {
        val archetype = checkNotNull(builds.findArchetypeById(archetypeId)) {
            "Partial expanded development fixture: $name archetype is missing"
        }
        check(archetype.name == name) {
            "Partial expanded development fixture: $name archetype name changed"
        }

        val variant = checkNotNull(builds.findVariantById(variantId)) {
            "Partial expanded development fixture: $name variant is missing"
        }
        check(variant.buildArchetypeId == archetypeId) {
            "Partial expanded development fixture: $name variant belongs to a different archetype"
        }
        check(variant.label == null) {
            "Partial expanded development fixture: $name variant label changed"
        }

        val revision = checkNotNull(builds.findRevisionById(revisionId)) {
            "Partial expanded development fixture: $name revision is missing"
        }
        check(revision.buildVariantId == variantId) {
            "Partial expanded development fixture: $name revision belongs to a different variant"
        }
        check(revision.compatibilityVersion == version) {
            "Partial expanded development fixture: $name revision compatibility changed"
        }
        check(revision.status == BuildVariantRevisionStatus.ACTIVE) {
            "Partial expanded development fixture: $name revision is no longer active"
        }

        val actualGroups = revision.requirementGroups.map { Triple(it.importance, it.logic, it.requirements) }
        val expectedGroups = groups.map { Triple(it.importance, it.logic, it.requirements) }
        check(actualGroups == expectedGroups) {
            "Partial expanded development fixture: $name revision requirements changed"
        }
    }

    private fun createSource(
        bossId: BossDefinitionId,
        sourceId: AcquisitionSourceId,
        name: String,
        availability: AcquisitionSourceAvailability,
    ) {
        bosses.create(BossDefinition(bossId, name))
        acquisition.saveSource(AcquisitionSource(sourceId))
        encounters.recordSnapshot(sourceId, scope, definitions.snapshot(bossId, name, availability), null)
    }

    private fun verifySource(
        bossId: BossDefinitionId,
        sourceId: AcquisitionSourceId,
        name: String,
        availability: AcquisitionSourceAvailability,
    ) {
        check(
            bosses.findById(bossId)?.displayName == name &&
                acquisition.findSourceById(sourceId) != null &&
                encounters.findTimeline(sourceId, scope)?.snapshots ==
                listOf(definitions.snapshot(bossId, name, availability)),
        ) { "Partial expanded development fixture: $name encounter knowledge" }
    }

    private fun createRelationship(
        relationshipId: DropRelationshipId,
        sourceId: AcquisitionSourceId,
        uniqueId: UniqueDefinitionId,
        state: DropRelationshipState,
    ) {
        acquisition.saveRelationship(DropRelationship(relationshipId, sourceId, uniqueId))
        relationships.recordSnapshot(relationshipId, scope, DropRelationshipSnapshot(null, state), null)
    }

    private fun verifyRelationship(
        relationshipId: DropRelationshipId,
        sourceId: AcquisitionSourceId,
        uniqueId: UniqueDefinitionId,
        state: DropRelationshipState,
    ) {
        check(
            acquisition.findRelationshipById(relationshipId)?.let {
                it.acquisitionSourceId == sourceId && it.uniqueDefinitionId == uniqueId
            } == true &&
                relationships.findTimeline(relationshipId, scope)?.snapshots ==
                listOf(DropRelationshipSnapshot(null, state)),
        ) { "Partial expanded development fixture: relationship $relationshipId" }
    }

    private fun publish(relationshipId: DropRelationshipId, id: DropEstimateId, chance: Double, label: String) {
        estimates.publish(relationshipId, scope, definitions.estimate(id, chance, label), null)
    }

    private fun verifyEstimate(relationshipId: DropRelationshipId, id: DropEstimateId, chance: Double, label: String) {
        val timeline = estimates.findAcceptedTimeline(relationshipId, scope)
        check(
            timeline?.snapshots == listOf(definitions.estimate(id, chance, label)) &&
                timeline?.withdrawals?.isEmpty() == true,
        ) { "Partial expanded development fixture: estimate for $label" }
    }

    private companion object {
        const val BOSS_P_CHANCE = 0.15
        const val BOSS_R_CHANCE = 0.05
        const val ADMIN_CHALLENGE_P_ATTEMPTS = 4
    }
}

private object ExpandedFixtureDefinitions {
    private val ids = DevelopmentFixtureIds

    fun snapshot(
        bossId: BossDefinitionId,
        label: String,
        availability: AcquisitionSourceAvailability,
    ): BossEncounterKnowledgeSnapshot =
        BossEncounterKnowledgeSnapshot(null, label, availability, BossParticipation.Fixed(setOf(bossId)))

    fun estimate(id: DropEstimateId, chance: Double, label: String): FixedDropEstimateSnapshot =
        FixedDropEstimateSnapshot(
            id = id,
            effectiveFromPatch = null,
            model = FixedBernoulliModel(chance),
            provenance = DropEstimateProvenance.MEASURED,
            evidenceReference = "synthetic-development-fixture:$label",
            reviewedAt = Instant.parse("2026-10-01T00:00:00Z"),
        )

    fun standardLeague(): LeagueDefinition = LeagueDefinition(
        id = ids.standardLeague,
        name = "Development Standard SSF",
        type = LeagueType.STANDARD,
        participation = LeagueParticipation.SSF,
        mortality = LeagueMortality.SOFTCORE,
        realm = GameRealm.PC,
    )

    fun buildPRequirements(): List<RequirementGroup> = listOf(
        RequirementGroup(
            RequirementImportance.ENABLING,
            RequirementLogic.ANY,
            listOf(Requirement(ids.uniqueD, 1), Requirement(ids.uniqueE, 1)),
        ),
        RequirementGroup(
            RequirementImportance.CORE,
            RequirementLogic.ALL,
            listOf(Requirement(ids.uniqueF, 1), Requirement(ids.uniqueG, 1)),
        ),
    )

    fun buildQRequirements(): List<RequirementGroup> = listOf(
        RequirementGroup(
            RequirementImportance.CORE,
            RequirementLogic.ALL,
            listOf(Requirement(ids.uniqueH, 2)),
        ),
        RequirementGroup(
            RequirementImportance.UPGRADE,
            RequirementLogic.ANY,
            listOf(Requirement(ids.uniqueI, 1), Requirement(ids.uniqueJ, 1)),
        ),
    )

    fun buildRRequirements(): List<RequirementGroup> = listOf(
        RequirementGroup(
            RequirementImportance.UPGRADE,
            RequirementLogic.ALL,
            listOf(Requirement(ids.uniqueK, 1), Requirement(ids.uniqueL, 1)),
        ),
    )
}
