package com.colinmoerbe.poecompanion.league.persistence

import org.springframework.data.jpa.repository.JpaRepository

internal interface SpringDataAccountEvaluationSelectionRepository :
    JpaRepository<AccountEvaluationSelectionEntity, Int>
