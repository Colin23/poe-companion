package com.colinmoerbe.poecompanion.build.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataRequirementRepository : JpaRepository<RequirementEntity, RequirementEntityId> {

    @Query(
        """
        SELECT entity
        FROM RequirementEntity entity
        WHERE entity.id.buildVariantRevisionId = :revisionId
        ORDER BY entity.id.groupPosition, entity.id.position
        """,
    )
    fun findAllByRevisionId(@Param("revisionId") revisionId: UUID): List<RequirementEntity>

    @Modifying
    @Query(
        """
        DELETE FROM RequirementEntity entity
        WHERE entity.id.buildVariantRevisionId = :revisionId
        """,
    )
    fun deleteAllByRevisionId(@Param("revisionId") revisionId: UUID)
}
