package org.abimon.umbra.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

import java.util.jar.JarFile

class UmbraPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.repositories.add(
                project.repositories.maven({ MavenArtifactRepository maven ->
                    maven.url = new URI("https://maven.abimon.org")
                })
        )

        project.getConfigurations().getByName("compile")
                .dependencies.add(project.dependencies.create("org.abimon:umbra-resolver:1.1.0"))


        def umbraConfiguration = project.getConfigurations().create("umbra")
                .setDescription("Tell Umbra to include this dependency")

        project.getConfigurations().getByName("compileOnly")
                .extendsFrom(umbraConfiguration)

        def umbraTask = project.task("assembleUmbra")
                .dependsOn(project.getTasksByName("classes", true).first())
                .doLast {
                    def umbraParent = new File(project.getBuildDir(), "resources${File.separator}main${File.separator}META-INF")
                    umbraParent.mkdirs()
                    def umbraProperties = new File(umbraParent, ".umbra")
                    def umbraOut = new PrintStream(umbraProperties)

                    try {
                        project.getRepositories().stream()
                                .filter({ repo -> repo instanceof MavenArtifactRepository })
                                .flatMap({ repo ->
                                    def urls = ((MavenArtifactRepository) repo).artifactUrls
                                    urls.add(((MavenArtifactRepository) repo).url)
                                    urls.stream()
                                })
                                .forEach({ uri -> umbraOut.println("repository: ${uri.toString()}") })

                        umbraConfiguration.resolvedConfiguration.resolvedArtifacts.forEach({ artifact ->
                            def jarFile = new JarFile(artifact.file)

                            try {
                                def group = artifact.moduleVersion.id.group.replace('.', '/')
                                def filename = jarFile.entries().iterator()
                                        .collect { entry -> entry.name }
                                        .stream()
                                        .filter({ str -> str.endsWith("class") && !str.contains("\$") })
                                        .max({ String o1, String o2 ->
                                            o1.contains(group) && o1.contains(group) ? (o1.contains("api") && o2.contains("api") ? 0 : o1.contains("api") ? -1 : o2.contains("api") ? 1 : 0)
                                                    : o1.contains(group) ? 1
                                                    : o2.contains(group) ? -1 : 0
                                        })

                                if (filename.isPresent()) {
                                    umbraOut.println("dependency: ${artifact.moduleVersion.id.group}:${artifact.moduleVersion.id.name}:${artifact.moduleVersion.id.version}:${filename.get()}")
                                }
                            } finally {
                                jarFile.close()
                            }
                        })
                    } finally {
                        umbraOut.close()
                    }
                }

        project.getTasksByName("assemble", true).first().dependsOn(umbraTask)
        project.getTasksByName("shadowJar", true).forEach({ task -> task.dependsOn(umbraTask) })
    }
}
