package com.colinmoerbe.poecompanion.league.persistence

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

internal interface SpringDataAccountContextRepository : JpaRepository<AccountContextEntity, UUID>
