package com.colinmoerbe.poecompanion.acquisition.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataBossDefinitionRepository : JpaRepository<BossDefinitionEntity, UUID> {
    @Modifying
    @Query(
        value =
        """
            INSERT INTO poe_companion.boss_definition (id, display_name)
            VALUES (:id, :displayName)
            ON CONFLICT (id) DO NOTHING
            """,
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("id") id: UUID, @Param("displayName") displayName: String): Int
}
