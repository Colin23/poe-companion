plugins {
    // Apply the foojay-resolver plugin to allow automatic downloads of JDKs.
    // This will only trigger if no local Java version matches the necessary version specified in the toolchain.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "poe-companion"
