import de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis
import org.cyclonedx.Version
import org.cyclonedx.model.Component
import org.gradle.api.file.RegularFile

plugins {
    java
    idea
    checkstyle
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.freefair.lombok") version "9.7.0"
    id("de.thetaphi.forbiddenapis") version "3.11"
    id("org.cyclonedx.bom") version "3.4.1"
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

dependencyManagement {
    imports {
        mavenBom ("org.springframework.boot:spring-boot-dependencies:4.1.1")
        mavenBom("org.junit:junit-bom:6.1.3")
        mavenBom("com.google.guava:guava-bom:33.7.1-jre")
    }
}

val slf4jVersion = "2.0.19"
val jakartaVersion = "3.0.0"
val archunitVersion = "1.5.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-docker-compose")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.google.guava:guava")
    implementation("org.slf4j:slf4j-api")
    implementation("jakarta.annotation:jakarta.annotation-api:$jakartaVersion")
    implementation("org.postgresql:postgresql")
    implementation("org.apache.httpcomponents.client5:httpclient5") // Necessary so that PATCH requests work

    developmentOnly("org.springframework.boot:spring-boot-devtools") // Ctrl+F9 for recompiling, this fast restarts the server

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // ArchitectureTests dependencies.
    testImplementation("com.tngtech.archunit:archunit:$archunitVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:")
    testImplementation("org.testcontainers:testcontainers-postgresql")
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
    bundledSignatures = setOf("jdk-unsafe", "jdk-deprecated", "jdk-internal", "jdk-non-portable", "jdk-reflection")
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
