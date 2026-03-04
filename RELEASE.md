## How to release this project
- check if version in `gradle.properties` is correct
- tag release with `v<version>`, this triggers release action `.github/workflows/release.yml`
- check status on gradle plugin portal: https://plugins.gradle.org/plugin/org.spdx.sbom
- create a post release PR to update version in `gradle.properties`
