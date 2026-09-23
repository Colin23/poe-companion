import com.sun.imageio.plugins.jpeg.JPEG.vendor
import de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis
import jdk.internal.misc.PreviewFeatures.isEnabled
import org.cyclonedx.Version
import org.cyclonedx.model.Component
import org.gradle.api.file.RegularFile

plugins {
    java
    idea
    checkstyle
    alias(libs.plugins.de.thetaphi.forbiddenapis)
    alias(libs.plugins.io.freefair.lombok)
    alias(libs.plugins.io.spring.dependencyManagement)
    alias(libs.plugins.org.springframework.boot.springBoot)
    alias(libs.plugins.org.cyclonedx.bom)
}

group = "com.colinmoerbe"
version = "0.0.1-SNAPSHOT"

springBoot { // Exposed additional information about the application to the /info actuator endpoint.
    buildInfo()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

repositories {
    mavenCentral()
}

val archunitVersion = "1.5.0"

dependencies {
    implementation(libs.org.springframework.boot.springBootDockerCompose)
    implementation(libs.org.springframework.boot.springBootStarterActuator)
    implementation(libs.org.springframework.boot.springBootStarterDataJpa)
    implementation(libs.org.springframework.boot.springBootStarterLiquibase)
    implementation(libs.org.springframework.boot.springBootStarterValidation)
    implementation(libs.org.springframework.boot.springBootStarterWeb)

    developmentOnly(libs.org.springframework.boot.springBootDevtools)

    runtimeOnly(libs.org.postgresql.postgresql)

    testImplementation("com.tngtech.archunit:archunit:$archunitVersion")
    testImplementation(libs.org.springframework.boot.springBootStarterTest)
    testImplementation(libs.org.springframework.boot.springBootStarterWebmvcTest)
    testImplementation(libs.org.springframework.boot.springBootTestcontainers)
    testImplementation(libs.org.testcontainers.junitJupiter)
    testImplementation(libs.org.testcontainers.postgresql)
}

tasks.withType<Jar> {
    manifest {
        attributes["Implementation-Version"] = version
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<DefaultTask>("checkstyleMain").configure {
    isEnabled = true
}

tasks.named<DefaultTask>("checkstyleTest").configure {
    isEnabled = true
}

tasks.named<CheckForbiddenApis>("forbiddenApisMain").configure {
    bundledSignatures =
        setOf("jdk-unsafe", "jdk-deprecated", "jdk-internal", "jdk-non-portable", "jdk-system-out", "jdk-reflection")
    signaturesFiles = project.files("config/forbidden-apis.txt")
    isEnabled = true
    setExcludes(setOf("**/api/**/*.class")) // This has to reference the .class files in the build dir.
}

tasks.named<CheckForbiddenApis>("forbiddenApisTest").configure {
    bundledSignatures = setOf("jdk-unsafe", "jdk-deprecated", "jdk-internal", "jdk-non-portable", "jdk-system-out", "jdk-reflection")
    signaturesFiles = project.files("config/forbidden-apis.txt")
    isEnabled = true
}

tasks.named("check").configure {
    dependsOn(tasks.named("forbiddenApisMain"))
}


tasks.cyclonedxDirectBom {
    projectType = Component.Type.APPLICATION

    // CycloneDX 1.7; default is 1.6
    schemaVersion = Version.VERSION_17

    // Usually these are the configurations you actually want in an app SBOM
    includeConfigs = listOf(
        "compileClasspath",
        "runtimeClasspath"
    )

    // Don't include test dependencies
    skipConfigs = listOf("(?i).*test.*")

    includeLicenseText = false
    includeBomSerialNumber = true
    includeMetadataResolution = true

    jsonOutput =
        layout.buildDirectory.file("reports/cyclonedx/bom.json")

    // Optional: disable XML if you only need JSON
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

checkstyle {
    configFile = project.file("config/checkstyle.xml")
    toolVersion = "14.1.0"
}
