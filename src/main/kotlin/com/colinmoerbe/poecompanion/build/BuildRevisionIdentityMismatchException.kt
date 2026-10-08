package com.colinmoerbe.poecompanion.build

/**
 * Signals that an existing build revision ID was reconstructed with a different immutable scope.
 */
class BuildRevisionIdentityMismatchException internal constructor(message: String) : RuntimeException(message)
