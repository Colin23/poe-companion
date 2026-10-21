package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.acquisition.probability.DropEstimateId
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionId
import com.colinmoerbe.poecompanion.build.identity.BuildArchetypeId
import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import java.util.UUID

/**
 * Synthetic development identities, deliberately unrelated to any external provider.
 *
 * A/B/C are display aliases, not canonical UniqueDefinition names. The IDs never change between startups.
 */
internal object DevelopmentFixtureIds {
    val uniqueA = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005001"))
    val uniqueB = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005002"))
    val uniqueC = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005003"))
    val league = LeagueDefinitionId(id("00000000-0000-4000-8000-000000005004"))
    val account = AccountContextId(id("00000000-0000-4000-8000-000000005005"))
    val archetype = BuildArchetypeId(id("00000000-0000-4000-8000-000000005006"))
    val variant = BuildVariantId(id("00000000-0000-4000-8000-000000005007"))
    val revision = BuildVariantRevisionId(id("00000000-0000-4000-8000-000000005008"))
    val boss = BossDefinitionId(id("00000000-0000-4000-8000-000000005009"))
    val source = AcquisitionSourceId(id("00000000-0000-4000-8000-000000005010"))
    val relationship = DropRelationshipId(id("00000000-0000-4000-8000-000000005011"))
    val estimate = DropEstimateId(id("00000000-0000-4000-8000-000000005012"))

    // Additional fixture IDs are additive; never change existing A/B/C identities.
    val uniqueD = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005013"))
    val uniqueE = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005014"))
    val uniqueF = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005015"))
    val uniqueG = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005016"))
    val uniqueH = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005017"))
    val uniqueI = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005018"))
    val uniqueJ = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005019"))
    val uniqueK = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005020"))
    val uniqueL = UniqueDefinitionId(id("00000000-0000-4000-8000-000000005021"))
    val standardLeague = LeagueDefinitionId(id("00000000-0000-4000-8000-000000005060"))
    val adminChallengeAccount = AccountContextId(id("00000000-0000-4000-8000-000000005061"))
    val adminStandardAccount = AccountContextId(id("00000000-0000-4000-8000-000000005062"))
    val archetypeP = BuildArchetypeId(id("00000000-0000-4000-8000-000000005063"))
    val variantP = BuildVariantId(id("00000000-0000-4000-8000-000000005064"))
    val revisionP = BuildVariantRevisionId(id("00000000-0000-4000-8000-000000005065"))
    val archetypeQ = BuildArchetypeId(id("00000000-0000-4000-8000-000000005066"))
    val variantQ = BuildVariantId(id("00000000-0000-4000-8000-000000005067"))
    val revisionQ = BuildVariantRevisionId(id("00000000-0000-4000-8000-000000005068"))
    val archetypeR = BuildArchetypeId(id("00000000-0000-4000-8000-000000005069"))
    val variantR = BuildVariantId(id("00000000-0000-4000-8000-000000005070"))
    val revisionR = BuildVariantRevisionId(id("00000000-0000-4000-8000-000000005071"))
    val bossP = BossDefinitionId(id("00000000-0000-4000-8000-000000005072"))
    val sourceP = AcquisitionSourceId(id("00000000-0000-4000-8000-000000005073"))
    val relationshipPD = DropRelationshipId(id("00000000-0000-4000-8000-000000005074"))
    val relationshipPE = DropRelationshipId(id("00000000-0000-4000-8000-000000005075"))
    val estimateP = DropEstimateId(id("00000000-0000-4000-8000-000000005076"))
    val bossQ = BossDefinitionId(id("00000000-0000-4000-8000-000000005077"))
    val sourceQ = AcquisitionSourceId(id("00000000-0000-4000-8000-000000005078"))
    val relationshipQH = DropRelationshipId(id("00000000-0000-4000-8000-000000005079"))
    val bossR = BossDefinitionId(id("00000000-0000-4000-8000-000000005080"))
    val sourceR = AcquisitionSourceId(id("00000000-0000-4000-8000-000000005081"))
    val relationshipRK = DropRelationshipId(id("00000000-0000-4000-8000-000000005082"))
    val estimateR = DropEstimateId(id("00000000-0000-4000-8000-000000005083"))

    val uniqueLabels = mapOf(
        uniqueA to "Unique A", uniqueB to "Unique B", uniqueC to "Unique C",
        uniqueD to "Unique D", uniqueE to "Unique E", uniqueF to "Unique F",
        uniqueG to "Unique G", uniqueH to "Unique H", uniqueI to "Unique I",
        uniqueJ to "Unique J", uniqueK to "Unique K", uniqueL to "Unique L",
    )

    private fun id(value: String): UUID = UUID.fromString(value)
}
