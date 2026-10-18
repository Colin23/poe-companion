package com.colinmoerbe.poecompanion.acquisition.encounter.internal

/** A persisted encounter's participation rows contradict its accepted participation kind. */
class CorruptBossEncounterKnowledgeException internal constructor(message: String) : RuntimeException(message)
