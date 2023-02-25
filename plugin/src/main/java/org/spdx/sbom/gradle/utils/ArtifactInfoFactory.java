/*
 * Copyright 2023 The Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spdx.sbom.gradle.utils;

import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

public class ArtifactInfoFactory {
  private final Project project;
  private final List<MavenArtifactRepository> mvnRepos;
  private final HttpClient httpClient;

  private ArtifactInfoFactory(
      Project project, List<MavenArtifactRepository> mvnRepos, HttpClient httpClient) {
    this.project = project;
    this.mvnRepos = mvnRepos;
    this.httpClient = httpClient;
  }

  public static ArtifactInfoFactory newFactory(Project project) {
    RepositoryHandler repositoryHandler = project.getRepositories();
    List<MavenArtifactRepository> repoUrls =
        repositoryHandler
            .stream()
            .filter(MavenArtifactRepository.class::isInstance)
            .map(MavenArtifactRepository.class::cast)
            .collect(Collectors.toList());

    HttpClient httpClient = HttpClients.newClient(10);

    return new ArtifactInfoFactory(project, repoUrls, httpClient);
  }

  public ArtifactInfo getInfo(ResolvedDependency resolvedDependency)
      throws XmlPullParserException, IOException, InterruptedException {

    if (resolvedDependency.getModuleArtifacts().size() > 1) {
      throw new RuntimeException(
          "this plugin can't handle dependencies that download multiple artifacts, contact the developer");
    }
    if (resolvedDependency.getModuleArtifacts().size() == 0) {
      throw new RuntimeException("0 artifact?");
    }
    ResolvedArtifact ra =
        resolvedDependency.getModuleArtifacts().stream().findFirst().orElseThrow();
    File artifact = ra.getFile();
    String filename = ra.getFile().getName();

    // calculate both sha256 and sha1, yes this is inefficiently written without a teereader
    String sha256 =
        com.google.common.io.Files.asByteSource(artifact).hash(Hashing.sha256()).toString();
    String sha1 = com.google.common.io.Files.asByteSource(artifact).hash(Hashing.sha1()).toString();

    Optional<MavenProject> mavenProject = getMavenPom(resolvedDependency);

    return ImmutableArtifactInfo.builder()
        .licenses(mavenProject.map(MavenProject::getLicenses).orElse(Collections.emptyList()))
        .sourceURL(getSourceURI(resolvedDependency, filename).orElse(URI.create("")))
        .sha256(sha256)
        .sha1(sha1)
        .homepage(mavenProject.map(MavenProject::getUrl).map(URI::create).orElse(URI.create("")))
        .build();
  }

  private Optional<MavenProject> getMavenPom(ResolvedDependency resolvedDependency)
      throws IOException, XmlPullParserException {
    Dependency pom = project.getDependencies().create(resolvedDependency.getName() + "@pom");
    Configuration onetimeConfig = project.getConfigurations().detachedConfiguration(pom);

    Optional<Path> pomFile = onetimeConfig.resolve().stream().map(File::toPath).findFirst();
    if (pomFile.isPresent()) {
      return Optional.of(
          new MavenProject(new MavenXpp3Reader().read(Files.newInputStream(pomFile.get()))));
    }
    return Optional.empty();
  }

  // this is pretty best effort, we're mimicking gradle's artifact remote fetch selector, but
  // not actually using it. There doesn't appear any other way to obtain this information from
  // gradle currently
  private Optional<URI> getSourceURI(ResolvedDependency resolvedDependency, String filename)
      throws IOException, InterruptedException {
    String artifactRepoPath =
        resolvedDependency.getModuleGroup().replace(".", "/")
            + "/"
            + resolvedDependency.getModuleName()
            + "/"
            + resolvedDependency.getModuleVersion()
            + "/"
            + URLEncoder.encode(filename, StandardCharsets.UTF_8);
    for (MavenArtifactRepository repo : mvnRepos) {
      URI uri = repo.getUrl().resolve(artifactRepoPath);
      var checkExistence =
          HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
      var resp = httpClient.send(checkExistence, BodyHandlers.discarding());
      if (resp.statusCode() == 200) {
        return Optional.of(uri);
      }
    }
    return Optional.empty();
  }
}
