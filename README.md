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

Apply and configure the plugin
```kotlin
plugins {
  `java`
  ...
  id("org.spdx.sbom") version "0.0.1"
}
...
// there is no default build, you *must* specify a target
spdxSbom {
  targets {
    // create a target named "release",
    // this is used for the task name (spdxSbomForRelease)
    // and output file (release.spdx.json)
    create("release") {
      // optionally change the target configuration
      // configuration.set("myCustomConfiguration")
    }
  }
}
```

run sbom generation (use --stacktrace to report bugs)
```bash
./gradlew :spdxSbomForRelease
# or use the aggregate task spdxSbom to run all sbom tasks
# ./gradlew :spdxSbom

output in: build/spdx/release.spdx.json
```

Example output for the plugin run on this project is [example.sbom.json](example.sbom.json)

### Notes
We do pretty lazy license stuff (will be handled better later)

Current source control information is only determined from git

Output is always json
