package com.colinmoerbe.poecompanion.account.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataManualOwnershipRepository :
    JpaRepository<ManualOwnershipEntity, ManualOwnershipEntityId> {

    @Query("SELECT entity FROM ManualOwnershipEntity entity WHERE entity.id.accountContextId = :accountContextId")
    fun findAllByAccountContextId(
        @Param("accountContextId") accountContextId: UUID,
    ): List<ManualOwnershipEntity>
}
