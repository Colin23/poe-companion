package com.colinmoerbe.poecompanion.acquisition.attempts

import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId

/**
 * One successfully completed encounter at a particular canonical acquisition source.
 *
 * One multi-boss encounter remains one attempt. This type does not represent opening an invitation or merely
 * possessing the resources to run the encounter.
 */
data class CompletedBossEncounterAttempt(val acquisitionSourceId: AcquisitionSourceId)
