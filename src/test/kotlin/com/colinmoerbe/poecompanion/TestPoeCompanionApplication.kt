package com.colinmoerbe.poecompanion

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<PoeCompanionApplication>().with(TestcontainersConfiguration::class).run(*args)
}
