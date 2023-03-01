# spdx-gradle-plugin
A prototype spdx gradle plugin

⚠ This project is not ready for use to satisfy any real SBOM requirements ⚠

Try it out and see what works

## Usage
This plugin is not published to mavenCentral or gradlePluginPortal, you need to build and deploy
locally and then use in your project

Install into local maven
```
$ git clonse git@github.com:loosebazooka/spdx-gradle-plugin
$ ./gradlew publishToMavenLocal
```

Add mavenLocal as a plugin repository (settings.gradle.kts)
```
pluginManagement {
 repositories {
     mavenLocal()
     gradlePluginPortal()
 }
}
```

Apply to project
```kotlin
plugins {
  `java`
  ...
  id("org.spdx.sbom") version "0.0.1"
}
```

run sbom generation (use --stacktrace to report bugs)
```
./gradlew :spdxSbom --stacktrace

output in: build/spdx/spdx.sbom.json
```

Example output for the plugin run on this project is [example.sbom.json](example.sbom.json)

### Notes
We do pretty lazy license stuff (will be handled better later)

Current source control information is only determined from git

Task is not very configurable, no user injection of sbom parameters

Cannot determine information from dependencies pulled from private repositories

Output is always json
