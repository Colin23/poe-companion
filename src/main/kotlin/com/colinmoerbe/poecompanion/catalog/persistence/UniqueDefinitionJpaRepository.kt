package com.colinmoerbe.poecompanion.catalog.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface UniqueDefinitionJpaRepository : JpaRepository<UniqueDefinitionEntity, UUID>
