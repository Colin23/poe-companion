package com.colinmoerbe.poecompanion.acquisition.attempts.persistence

import org.springframework.data.jpa.repository.JpaRepository

internal interface SpringDataAvailableAttemptsRepository :
    JpaRepository<AvailableAttemptsEntity, AvailableAttemptsEntityId>
