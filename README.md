# spdx-gradle-plugin
An spdx gradle plugin

This project currently produces valid sboms for the projects we've tested. If it does not work for your case, please let us know in a new issue we can handle new use cases.

## Usage

#### Gradle Plugin Portal [![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/org/spdx/spdx-gradle-plugin/maven-metadata.xml.svg?colorB=007ec6&label=latest)](https://plugins.gradle.org/plugin/org.spdx.sbom)
This plugin is published to Gradle Plugin Portal: https://plugins.gradle.org/plugin/org.spdx.sbom

#### Local Development
You can build and deploy locally and then use in your project

Install into local maven
```bash
$ git clone git@github.com:spdx/spdx-gradle-plugin
$ ./gradlew publishToMavenLocal -Pskip.signing
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

### Basic Usage

Apply and configure the plugin
```kotlin
plugins {
  `java`
  ...
  id("org.spdx.sbom") version "0.6.0"
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
    // create a target named "release",
    // this is used for the task name (spdxSbomForRelease)
    // and output file (build/spdx/release.spdx.json)
    create("release") {
      // use a different configuration (or multiple configurations)
      configurations.set(listOf("myCustomConfiguration"))

      // override the default output file
      outputFile.set(layout.buildDirectory.file("custom-spdx.filename"))

      // provide scm info (usually from your CI)
      scm {
        uri.set("my-scm-repository")
        revision.set("asdfasdfasdf...")
      }

      // adjust properties of the document
      document {
        name.set("my spdx document")
        namespace.set("https://my.org/spdx/<some UUID>")
        creator.set("Person:Goose Loosebazooka")
        supplier.set("Organization:loosebazooka industries")

        // add an uber package on the document between the document and the
        // root module of the project being analyzed, you probably don't need this
        // but it's available if you want to describe the artifact in a special way
        uberPackage {
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
- Licensing and copyright is somewhat incomplete (works well for maven deps)
- Output is always json

### Experimental (do not use)

If you use these experimental features, they will change them whenever with no notification. They are
to support very specific build usecases and are not for general consumption

use `taskExtension` to map downloadLocations if they are cached somewhere other than original location
```kotlin
tasks.withType<SpdxSbomTask> {
   taskExtension.set(object : SpdxSbomTaskExtension {
       override fun mapRepoUri(input: URI, moduleId: ModuleVersionIdentifier): URI {
           // ignore input and return duck
           return URI.create("https://duck.com/repository")
       }
       override fun mapScmForProject(original: ScmInfo, projectInfo: ProjectInfo): ScmInfo {
           // ignore provided scminfo (from extension) and project info (the project we are looking for scm info)
           return ScmInfo.from("github.com/goose", "my-sha-is-also-a-goose")
       }
       override fun shouldCreatePackageForProject(projectInfo: ProjectInfo): Boolean {
           // return false to skip adding the project into SBOM if it doesn't represent an external dependency. All
           // dependencies of the skipped project will be analyzed and represented in the SBOM as dependencies of the
           // project's parent.
           return false
       }
   })
}
```

You can use the abstract class `DefaultSpdxSbomTaskExtension` if you don't want to implement all the methods
of the interface `SpdxSbomTaskExtension`.
