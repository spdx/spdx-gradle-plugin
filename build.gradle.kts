import org.gradle.api.plugins.jvm.JvmTestSuite

plugins {
    id("com.gradle.plugin-publish") version "2.1.1"
    id("com.diffplug.spotless") version "8.8.0"
    signing
    id("org.spdx.sbom") version "0.12.0"
}

group = "org.spdx"
description = "A gradle plugin generating spdx sboms"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(platform("org.immutables:bom:2.12.2"))
    annotationProcessor(platform("org.immutables:bom:2.12.2"))
    compileOnly("org.immutables:serial")
    compileOnly("org.immutables:value-annotations")
    annotationProcessor("org.immutables:value")

    implementation("org.spdx:java-spdx-library:2.0.3")
    implementation("org.spdx:spdx-jackson-store:2.0.6")
    implementation("org.apache.maven:maven-model-builder:3.9.16")
    implementation("org.apache.maven:maven-model:3.9.16")
    implementation("com.google.guava:guava:33.6.0-jre")
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
        create("spdxSbomSettings") {
            id = "org.spdx.sbom.settings"
            implementationClass = "org.spdx.sbom.gradle.SpdxSbomSettingsPlugin"
            displayName = "Collect project info for SPDX SBOMS"
            description = "This plugin collects project information needed for SPDX SBOMS in project-isolated builds"
            tags.set(listOf("spdx", "sbom"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter()
            dependencies {
                implementation(platform("org.junit:junit-bom:6.1.2"))
                implementation("org.junit.jupiter:junit-jupiter")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
                implementation("org.hamcrest:hamcrest-library:3.0")
                implementation("org.spdx:tools-java:2.0.5")
                implementation(gradleApi())
            }
        }
        val test = named<JvmTestSuite>("test")
        val functionalTest = register<JvmTestSuite>("functionalTest")
    }
}

gradlePlugin.testSourceSets(sourceSets.getByName("functionalTest"))

tasks.named<Task>("check") {
    dependsOn(tasks.named("functionalTest"))
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
        leadingTabsToSpaces()
        endWithNewline()
    }
    java {
        googleJavaFormat()
        licenseHeaderFile("$rootDir/config/licenseHeader")
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Sign> {
    onlyIf("skip.signing is not set") { !project.hasProperty("skip.signing") }
}

signing {
    val signingKey = project.findProperty("signingKey") as String?
    val signingPassword = project.findProperty("signingPassword") as String?
    useInMemoryPgpKeys(signingKey, signingPassword)
}

spdxSbom {
    targets {
        create("example") {
            scm {
                uri.set("github.com/spdx/spdx-gradle-plugin")
                revision.set("dev")
            }
        }
    }
}

tasks.register("updateExample", Copy::class) {
    from(tasks.named("spdxSbomForExample"))
    into(layout.projectDirectory)
}
