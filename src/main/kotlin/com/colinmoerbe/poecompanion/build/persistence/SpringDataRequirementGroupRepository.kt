package com.colinmoerbe.poecompanion.build.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataRequirementGroupRepository :
    JpaRepository<RequirementGroupEntity, RequirementGroupEntityId> {

    @Query(
        """
        SELECT entity
        FROM RequirementGroupEntity entity
        WHERE entity.id.buildVariantRevisionId = :revisionId
        ORDER BY entity.id.position
        """,
    )
    fun findAllByRevisionId(@Param("revisionId") revisionId: UUID): List<RequirementGroupEntity>

    @Modifying
    @Query(
        """
        DELETE FROM RequirementGroupEntity entity
        WHERE entity.id.buildVariantRevisionId = :revisionId
        """,
    )
    fun deleteAllByRevisionId(@Param("revisionId") revisionId: UUID)
}
