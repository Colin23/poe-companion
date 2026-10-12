package com.colinmoerbe.poecompanion.acquisition.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataDropRelationshipRepository : JpaRepository<DropRelationshipEntity, UUID> {
    @Modifying
    @Query(
        value =
        """
            INSERT INTO poe_companion.drop_relationship (id, acquisition_source_id, unique_definition_id)
            VALUES (:id, :acquisitionSourceId, :uniqueDefinitionId)
            ON CONFLICT (id) DO NOTHING
            """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("acquisitionSourceId") acquisitionSourceId: UUID,
        @Param("uniqueDefinitionId") uniqueDefinitionId: UUID,
    ): Int
}
