package com.colinmoerbe.poecompanion.acquisition.probability.persistence

/** Persistence discriminator; not a nullable numeric probability model. */
internal enum class FixedProbabilityKnowledgeKind {
    ESTIMATE,
    WITHDRAWAL,
}
