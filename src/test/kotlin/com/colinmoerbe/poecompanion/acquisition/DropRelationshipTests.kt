package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class DropRelationshipTests {

    @Test
    fun `generated relationship IDs should be distinct and reconstructable`() {
        val first = DropRelationshipId.generate()
        val second = DropRelationshipId.generate()

        assertThat(second).isNotEqualTo(first)
        assertThat(DropRelationshipId(first.value)).isEqualTo(first)
    }

    @Test
    fun `relationship should retain stable source and Unique identities`() {
        val id = DropRelationshipId.generate()
        val sourceId = AcquisitionSourceId.generate()
        val uniqueDefinitionId = UniqueDefinitionId(UUID.randomUUID())

        val relationship = DropRelationship(id, sourceId, uniqueDefinitionId)

        assertThat(relationship.id).isEqualTo(id)
        assertThat(relationship.acquisitionSourceId).isEqualTo(sourceId)
        assertThat(relationship.uniqueDefinitionId).isEqualTo(uniqueDefinitionId)
    }
}
