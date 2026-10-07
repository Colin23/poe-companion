package com.colinmoerbe.poecompanion.build.persistence

import com.colinmoerbe.poecompanion.build.RequirementImportance
import com.colinmoerbe.poecompanion.build.RequirementLogic
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * JPA representation of one ordered requirement group inside a build revision snapshot.
 */
@Entity
@Table(name = "build_requirement_group")
internal open class RequirementGroupEntity(
    @EmbeddedId
    open var id: RequirementGroupEntityId,
    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 32)
    open var importance: RequirementImportance,
    @Enumerated(EnumType.STRING)
    @Column(name = "logic", nullable = false, length = 32)
    open var logic: RequirementLogic,
)
