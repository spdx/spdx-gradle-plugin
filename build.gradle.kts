plugins {
    id("com.gradle.plugin-publish") version "1.2.0"
    id("com.diffplug.spotless") version "6.16.0"
//    id("org.spdx.sbom") version "0.1.0"
}

group = "org.spdx"
description = "A gradle plugin generating spdx sboms"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.immutables:serial:2.9.2")
    compileOnly("org.immutables:value-annotations:2.9.2")
    annotationProcessor("org.immutables:value:2.9.2")

    implementation("org.spdx:java-spdx-library:1.1.6")
    implementation("org.spdx:spdx-jackson-store:1.1.6")
    implementation("org.apache.maven:maven-core:3.9.0")
    implementation("com.google.guava:guava:31.1-jre")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.hamcrest:hamcrest-library:2.2")
    testImplementation("org.spdx:tools-java:1.1.6")
}

gradlePlugin {
    website.set("https://github.com/spdx/spdx-gradle-plugin")
    vcsUrl.set("https://github.com/spdx/spdx-gradle-plugin.git")
    plugins {
        create("spdxSbom") {
            id = "org.spdx.sbom"
            implementationClass = "org.spdx.sbom.gradle.SpdxSbomPlugin"
            displayName = "Generate sboms in spdx format"
            description = "This plugin generates json formatted spdx sboms for gradle projects"
            tags.set(listOf("spdx", "sbom"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val functionalTestSourceSet = sourceSets.create("functionalTest") {}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])

val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

gradlePlugin.testSourceSets(functionalTestSourceSet)

tasks.named<Task>("check") {
    // Run the functional tests as part of `check`
    dependsOn(functionalTest)
}

tasks.named<Test>("test") {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
}

tasks.named<Javadoc>("javadoc") {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}

spotless {
    kotlinGradle {
        target("*.gradle.kts") // default target for kotlinGradle
        ktlint()
    }
    format("misc") {
        target("*.md", ".gitignore", "**/*.yaml")

        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
    java {
        googleJavaFormat("1.6")
        licenseHeaderFile("$rootDir/config/licenseHeader")
    }
}

// spdxSbom {
//     targets {
//         create("example") {
//         }
//     }
// }
