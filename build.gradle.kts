import de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis
import org.cyclonedx.Version
import org.cyclonedx.model.Component
import org.gradle.api.file.RegularFile
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    idea
    alias(libs.plugins.com.diffplug.spotless)
    alias(libs.plugins.de.thetaphi.forbiddenapis)
    alias(libs.plugins.dev.detekt)
    alias(libs.plugins.org.cyclonedx.bom)
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.jpa)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.spring)
    alias(libs.plugins.org.springframework.boot.springBoot)
}

group = "com.colinmoerbe"
version = "0.0.1-SNAPSHOT"

springBoot {
    // Exposed additional information about the application to the /info actuator endpoint.
    buildInfo()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

repositories {
    mavenCentral()
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(libs.org.springframework.modulith.springModulithBom))

    implementation(libs.org.flywaydb.flywayDatabasePostgresql)
    implementation(libs.org.jetbrains.kotlin.kotlinReflect)
    implementation(libs.org.springframework.boot.springBootStarterActuator)
    implementation(libs.org.springframework.boot.springBootStarterDataJpa)
    implementation(libs.org.springframework.boot.springBootStarterFlyway)
    implementation(libs.org.springframework.boot.springBootStarterSecurity)
    implementation(libs.org.springframework.boot.springBootStarterThymeleaf)
    implementation(libs.org.springframework.boot.springBootStarterValidation)
    implementation(libs.org.springframework.boot.springBootStarterWebmvc)
    implementation(libs.org.springframework.modulith.springModulithStarterCore)
    implementation(libs.org.thymeleaf.extras.thymeleafExtrasSpringSecurity6)
    implementation(libs.tools.jackson.module.jacksonModuleKotlin)

    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly(libs.org.springframework.boot.springBootDevtools)
    developmentOnly(libs.org.springframework.boot.springBootDockerCompose)

    runtimeOnly(libs.org.postgresql.postgresql)

    testImplementation(libs.org.springframework.boot.springBootStarterSecurityTest)
    testImplementation(libs.org.springframework.boot.springBootStarterTest)
    testImplementation(libs.org.springframework.boot.springBootTestcontainers)
    testImplementation(libs.org.springframework.boot.springBootStarterWebmvcTest)
    testImplementation(libs.org.springframework.modulith.springModulithStarterTest)
    testImplementation(libs.org.testcontainers.junitJupiter)
    testImplementation(libs.org.testcontainers.postgresql)

    testRuntimeOnly(libs.org.junit.platform.junitPlatformLauncher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
        )
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_code_style" to "intellij_idea",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts", "gradle/**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_code_style" to "intellij_idea",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target(
            "*.md",
            "*.yml",
            "*.yaml",
            "*.toml",
            "*.properties",
            ".editorconfig",
            ".gitattributes",
            ".gitignore",
            "docs/**/*.md",
            ".github/**/*.yml",
            ".github/**/*.yaml",
        )
        targetExclude(
            "build/**",
            ".gradle/**",
            ".idea/**",
            ".kotlin/**",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
}

tasks.withType<Jar> {
    manifest {
        attributes["Implementation-Version"] = version
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<BootBuildImage>("bootBuildImage") {
    environment.put(
        "BP_JVM_VERSION",
        java.toolchain.languageVersion.map { it.asInt().toString() },
    )
}

tasks.named<CheckForbiddenApis>("forbiddenApisMain") {
    bundledSignatures =
        setOf(
            "jdk-unsafe",
            "jdk-deprecated",
            "jdk-internal",
            "jdk-non-portable",
            "jdk-system-out",
            "jdk-reflection",
        )
    signaturesFiles = project.files("config/forbidden-apis.txt")
    isEnabled = true
    setExcludes(setOf("**/api/**/*.class")) // This has to reference the .class files in the build dir.
}

tasks.named<CheckForbiddenApis>("forbiddenApisTest") {
    bundledSignatures =
        setOf(
            "jdk-unsafe",
            "jdk-deprecated",
            "jdk-internal",
            "jdk-non-portable",
            "jdk-system-out",
            "jdk-reflection",
        )
    signaturesFiles = project.files("config/forbidden-apis.txt")
    isEnabled = true
}

tasks.named("check") {
    dependsOn(tasks.named("forbiddenApisMain"))
    dependsOn(tasks.named("forbiddenApisTest"))
}

tasks.cyclonedxDirectBom {
    projectType = Component.Type.APPLICATION
    schemaVersion = Version.VERSION_17

    includeConfigs =
        listOf(
            "compileClasspath",
            "runtimeClasspath",
        )

    skipConfigs = listOf("(?i).*test.*")
    includeLicenseText = false
    includeBomSerialNumber = true
    // Keep metadata enrichment disabled: it performs additional Maven POM resolution that can
    // fall outside Renovate's dependency-verification metadata update path.
    // See docs/development/dependency-management.md.
    includeMetadataResolution = false
    jsonOutput = layout.buildDirectory.file("reports/cyclonedx/bom.json")
    xmlOutput.convention(null as RegularFile?)
}

// sourceSets["main"].java.srcDirs("src/main/gen") Mark generated directories as source directories.

idea {
    module {
        // generatedSourceDirs.add(project.file("src/main/gen")) Only needed when an additional source directory is needed.
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
