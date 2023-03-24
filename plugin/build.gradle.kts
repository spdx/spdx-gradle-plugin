plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.diffplug.spotless") version "6.16.0"
//    id("org.spdx.sbom") version "0.0.1"
}

version = "0.0.1"
description = "A gradle plugin generating spdx sboms"
val repoUrl = "github.com/loosebazoka/spdx-gradle-plugin"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.immutables:serial:2.9.2")
    compileOnly("org.immutables:value-annotations:2.9.2")
    annotationProcessor("org.immutables:value:2.9.2")

    implementation("org.spdx:java-spdx-library:1.1.4")
    implementation("org.spdx:spdx-jackson-store:1.1.4")
    implementation("org.spdx:spdx-rdf-store:1.1.4")
    implementation("org.apache.maven:maven-core:3.9.0")
    implementation("com.google.guava:guava:31.1-jre")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.hamcrest:hamcrest-library:2.2")
}

gradlePlugin {
    val spdxsbom by plugins.creating {
        id = "org.spdx.sbom"
        implementationClass = "org.spdx.sbom.gradle.SpdxSbomPlugin"
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.gradle.sample"
            artifactId = "library"
            version = "1.1"

            from(components["java"])
            pom {
                name.set(project.name)
                description.set(project.description)
                inceptionYear.set("2023")
                url.set(repoUrl)
                organization {
                    name.set("loosebazooka temp")
                    url.set("https://github.com/loosebazooka")
                }
                developers {
                    developer {
                        organization.set("loosebazooka temp")
                        organizationUrl.set("https://github.com/loosebazooka")
                    }
                }
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("$repoUrl/issues")
                }
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set("scm:git:$repoUrl.git")
                    developerConnection.set("scm:git:$repoUrl.git")
                    url.set(repoUrl)
                    tag.set("HEAD")
                }
            }
        }
    }
}

// spdxSbom {
//  targets {
//    create("example"){
//    }
//  }
// }
