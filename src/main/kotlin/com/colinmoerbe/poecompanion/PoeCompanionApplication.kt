package com.colinmoerbe.poecompanion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PoeCompanionApplication

fun main(args: Array<String>) {
    runApplication<PoeCompanionApplication>(*args)
}
