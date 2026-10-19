package com.colinmoerbe.poecompanion.acquisition.probability

/**
 * A fixed estimate publication lost a concurrent curator decision for the same exact probability key.
 *
 * The application does not retry automatically because doing so could silently replace the other publication.
 */
class FixedDropEstimatePublicationConflictException internal constructor(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
