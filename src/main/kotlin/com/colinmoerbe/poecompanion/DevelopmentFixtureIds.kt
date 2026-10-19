package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.acquisition.probability.DropEstimateId
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionId
import com.colinmoerbe.poecompanion.build.BuildArchetypeId
import com.colinmoerbe.poecompanion.build.BuildVariantId
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionId
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

    val uniqueLabels = mapOf(uniqueA to "Unique A", uniqueB to "Unique B", uniqueC to "Unique C")

    private fun id(value: String): UUID = UUID.fromString(value)
}
