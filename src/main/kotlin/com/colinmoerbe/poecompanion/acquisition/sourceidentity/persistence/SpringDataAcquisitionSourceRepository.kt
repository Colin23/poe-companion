package com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface SpringDataAcquisitionSourceRepository : JpaRepository<AcquisitionSourceEntity, UUID>
