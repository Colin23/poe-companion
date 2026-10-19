package com.colinmoerbe.poecompanion.build.identity.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface SpringDataBuildVariantRepository : JpaRepository<BuildVariantEntity, UUID>
