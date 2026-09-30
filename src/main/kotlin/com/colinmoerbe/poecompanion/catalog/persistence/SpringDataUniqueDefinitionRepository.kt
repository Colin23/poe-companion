package com.colinmoerbe.poecompanion.catalog.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data access to the JPA representation of unique definitions.
 */
internal interface SpringDataUniqueDefinitionRepository : JpaRepository<UniqueDefinitionEntity, UUID>
