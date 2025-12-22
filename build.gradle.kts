plugins {
    id("com.gradle.plugin-publish") version "2.0.0"
    id("com.diffplug.spotless") version "8.1.0"
    signing
    id("org.spdx.sbom") version "0.9.0"
}

group = "org.spdx"
description = "A gradle plugin generating spdx sboms"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(platform("org.immutables:bom:2.12.0"))
    annotationProcessor(platform("org.immutables:bom:2.12.0"))
    compileOnly("org.immutables:serial")
    compileOnly("org.immutables:value-annotations")
    annotationProcessor("org.immutables:value")

    implementation("org.spdx:java-spdx-library:2.0.2")
    implementation("org.spdx:spdx-jackson-store:2.0.4")
    implementation("org.apache.maven:maven-model-builder:3.9.12")
    implementation("org.apache.maven:maven-model:3.9.12")
    implementation("com.google.guava:guava:33.5.0-jre")

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher") // https://github.com/junit-team/junit5/issues/4374
    testImplementation("org.hamcrest:hamcrest-library:3.0")
    testImplementation("org.spdx:tools-java:2.0.4")
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val functionalTestSourceSet = sourceSets.create("functionalTest") {}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

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
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

spdxSbom {
    targets {
        create("example") {
        }
    }
}

tasks.register("updateExample", Copy::class) {
    from(tasks.named("spdxSbomForExample"))
    into(layout.projectDirectory)
}
