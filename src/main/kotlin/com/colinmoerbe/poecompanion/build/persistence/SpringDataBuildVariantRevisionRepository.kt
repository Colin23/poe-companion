package com.colinmoerbe.poecompanion.build.persistence

import com.colinmoerbe.poecompanion.build.BuildVariantRevisionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataBuildVariantRevisionRepository : JpaRepository<BuildVariantRevisionEntity, UUID> {

    @Query(
        """
        SELECT entity
        FROM BuildVariantRevisionEntity entity
        WHERE entity.buildVariantId = :buildVariantId
          AND entity.compatibilityVersionMajor = :compatibilityVersionMajor
          AND entity.compatibilityVersionMinor = :compatibilityVersionMinor
          AND entity.status = :status
        """,
    )
    fun findByScopeAndStatus(
        @Param("buildVariantId") buildVariantId: UUID,
        @Param("compatibilityVersionMajor") compatibilityVersionMajor: Int,
        @Param("compatibilityVersionMinor") compatibilityVersionMinor: Int,
        @Param("status") status: BuildVariantRevisionStatus,
    ): BuildVariantRevisionEntity?

    @Modifying
    @Query(
        """
        UPDATE BuildVariantRevisionEntity entity
        SET entity.status = :newStatus
        WHERE entity.id = :id
          AND entity.status = :expectedStatus
        """,
    )
    fun updateStatusIfCurrent(
        @Param("id") id: UUID,
        @Param("expectedStatus") expectedStatus: BuildVariantRevisionStatus,
        @Param("newStatus") newStatus: BuildVariantRevisionStatus,
    ): Int
}
