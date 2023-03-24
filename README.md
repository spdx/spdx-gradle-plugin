# spdx-gradle-plugin
A prototype spdx gradle plugin

⚠ This project is not ready for use to satisfy any real SBOM requirements ⚠

Try it out and see what works, don't depend on it yet, it will probably change

## Usage
This plugin is not published to mavenCentral or gradlePluginPortal, you need to build and deploy
locally and then use in your project

Install into local maven
```bash
$ git clonse git@github.com:loosebazooka/spdx-gradle-plugin
$ ./gradlew publishToMavenLocal
```

Add mavenLocal as a plugin repository (settings.gradle.kts)
```kotlin
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
      // configure here
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

Example output for the plugin run on this project is [example.spdx.json](example.spdx.json)

### Configuration

Tasks can be configured via the extension
```kotlin
spdxSbom {
  targets {
    create("release") {
      // use a different configuration
      configuration.set("myCustomConfiguration")

      // adjust properties of the document
      document {
        name.set("my spdx document")
        namespace.set("https://my.org/spdx/<some UUID>")
        creator.set("Person:Lucy Loosebazooka")

        // add a root spdx package on the document between the document and the 
        // root module of the configuration being analyzed
        rootPackage { 
          // you must set all or none of these
          name.set("goose")
          version.set("1.2.3")
          supplier.set("Organization:loosebazooka industries")
        }
    }
    // optionally have multiple targets
    // create("another") {
    // }
  }
}
```

### Notes
We do pretty lazy license stuff (will be handled better later)

Current source control information is only determined from git

Output is always json

### Experimental

If you use these experimental features, I will change them whenever I want with no notification. They are 
to support very specific build usecases and are not for public consumption

use `taskExtension` to map downloadLocations if they are cached somewhere other than original location
```kotlin
tasks.withType<SpdxSbomTask>() {
   taskExtension.set(object : SpdxSbomTaskExtension {
       override fun mapDownloadUri(input: URI?): URI {
           // ignore input and return duck
           return URI.create("https://duck.com")
       }
   })
}
```
or shortened to
```kotlin
tasks.withType<SpdxSbomTask>() {
   taskExtension.set(SpdxSbomTaskExtension {
       URI.create("https://duck.com")
   })
}
```
