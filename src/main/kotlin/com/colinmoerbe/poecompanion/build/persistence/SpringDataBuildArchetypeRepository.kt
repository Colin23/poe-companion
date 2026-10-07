package com.colinmoerbe.poecompanion.build.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface SpringDataBuildArchetypeRepository : JpaRepository<BuildArchetypeEntity, UUID>
