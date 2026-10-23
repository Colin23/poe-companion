package com.colinmoerbe.poecompanion.league

/** A saved historical selection may be viewed, but is not a valid source for new personal state. */
class UnrecognizedSelectedGameEnvironmentException :
    IllegalStateException("This game environment is not currently recognized for new personal data")
