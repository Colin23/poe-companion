package com.colinmoerbe.poecompanion.acquisition.persistence

import org.springframework.data.jpa.repository.JpaRepository

internal interface SpringDataAvailableAttemptsRepository :
    JpaRepository<AvailableAttemptsEntity, AvailableAttemptsEntityId>
