package com.colinmoerbe.poecompanion.acquisition

/** A persisted encounter's participation rows contradict its accepted participation kind. */
class CorruptBossEncounterKnowledgeException internal constructor(message: String) : RuntimeException(message)
