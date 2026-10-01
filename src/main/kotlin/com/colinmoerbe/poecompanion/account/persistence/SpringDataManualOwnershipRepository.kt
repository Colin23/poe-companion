package com.colinmoerbe.poecompanion.account.persistence

import org.springframework.data.jpa.repository.JpaRepository

internal interface SpringDataManualOwnershipRepository :
    JpaRepository<ManualOwnershipEntity, ManualOwnershipEntityId>
