package com.colinmoerbe.poecompanion.build

/**
 * Role a requirement group plays in a build's Unique readiness.
 */
enum class RequirementImportance {
    ENABLING,
    CORE,
    UPGRADE,
}
