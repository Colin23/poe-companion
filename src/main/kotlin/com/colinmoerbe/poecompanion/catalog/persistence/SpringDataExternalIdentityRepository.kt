package com.colinmoerbe.poecompanion.catalog.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data access to the JPA representation of external identity mappings.
 */
internal interface SpringDataExternalIdentityRepository :
    JpaRepository<ExternalIdentityEntity, ExternalIdentityEntityId>
