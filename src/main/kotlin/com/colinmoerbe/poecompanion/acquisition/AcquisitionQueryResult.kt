package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import java.util.Collections

/**
 * Query result for known candidate sources of a Unique in the currently selected account/evaluation context.
 */
sealed interface AcquisitionQueryResult {
    data object NoAccountEvaluationSelected : AcquisitionQueryResult

    class Evaluated(
        val accountEvaluation: AccountEvaluation,
        val uniqueDefinitionId: UniqueDefinitionId,
        sources: List<AcquisitionSourceAssessment>,
    ) : AcquisitionQueryResult {
        val sources: List<AcquisitionSourceAssessment> = Collections.unmodifiableList(sources.toList())
    }
}
