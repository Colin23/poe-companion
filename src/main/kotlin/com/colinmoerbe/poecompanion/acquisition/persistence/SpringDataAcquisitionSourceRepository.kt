package com.colinmoerbe.poecompanion.acquisition.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface SpringDataAcquisitionSourceRepository : JpaRepository<AcquisitionSourceEntity, UUID>
