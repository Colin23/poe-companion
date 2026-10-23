package com.colinmoerbe.poecompanion.acquisition.readmodel

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import com.colinmoerbe.poecompanion.league.EvaluationContext
import java.util.Collections

/**
 * Query result for known candidate sources of a Unique in the currently selected game environment.
 */
sealed interface AcquisitionQueryResult {
    data object NoAccountEvaluationSelected : AcquisitionQueryResult

    class Evaluated(
        val accountEvaluation: AccountEvaluation?,
        val uniqueDefinitionId: UniqueDefinitionId,
        sources: List<AcquisitionSourceAssessment>,
        val evaluationContext: EvaluationContext,
    ) : AcquisitionQueryResult {
        val sources: List<AcquisitionSourceAssessment> = Collections.unmodifiableList(sources.toList())
    }
}
