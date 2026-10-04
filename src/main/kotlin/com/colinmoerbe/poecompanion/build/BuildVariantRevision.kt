package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import java.util.Collections

/**
 * Version-specific semantic definition of a build variant.
 *
 * New revisions start as drafts. Once activated, readiness-relevant semantic contents must no longer be edited.
 */
class BuildVariantRevision(
    val id: BuildVariantRevisionId,
    val buildVariantId: BuildVariantId,
    val compatibilityVersion: CompatibilityVersion,
) {
    var status: BuildVariantRevisionStatus = BuildVariantRevisionStatus.DRAFT
        private set

    var requirementGroups: List<RequirementGroup> = emptyList()
        private set

    fun updateRequirementGroups(requirementGroups: List<RequirementGroup>) {
        check(status == BuildVariantRevisionStatus.DRAFT) {
            "Only draft build revisions can change requirement groups"
        }
        this.requirementGroups = Collections.unmodifiableList(ArrayList(requirementGroups))
    }

    fun activate() {
        check(status == BuildVariantRevisionStatus.DRAFT) { "Only draft build revisions can be activated" }
        status = BuildVariantRevisionStatus.ACTIVE
    }

    fun supersede() {
        check(status == BuildVariantRevisionStatus.ACTIVE) { "Only active build revisions can be superseded" }
        status = BuildVariantRevisionStatus.SUPERSEDED
    }
}
