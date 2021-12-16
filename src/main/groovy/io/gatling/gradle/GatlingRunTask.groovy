package io.gatling.gradle

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec

import java.nio.file.Path
import java.nio.file.Paths

class GatlingRunTask extends DefaultTask implements JvmConfigurable {
    @Internal
    Closure simulations

    @OutputDirectory
    File gatlingReportDir = project.file("${project.reportsDir}/gatling")

    GatlingRunTask() {
        outputs.upToDateWhen { false }
    }

    @InputFiles
    FileTree getJavaSimulationSources() {
        def simulationFilter = this.simulations ?: project.gatling.simulations
        return project.sourceSets.gatling.java.matching(simulationFilter)
    }

    @InputFiles
    FileTree getKotlinSimulationSources() {
        if (project.sourceSets.gatling.hasProperty("kotlin")) {
            def simulationFilter = this.simulations ?: project.gatling.simulations
            return project.sourceSets.gatling.kotlin.matching(simulationFilter)
        } else {
            return project.files().asFileTree
        }
    }

    @InputFiles
    FileTree getScalaSimulationSources() {
        def simulationFilter = this.simulations ?: project.gatling.simulations
        return project.sourceSets.gatling.scala.matching(simulationFilter)
    }

    private static File classesDirForLanguage(FileCollection classesDirs, String language) {
        def classesDirsOfType = classesDirs.filter { it.parentFile.name == language }
        if (classesDirsOfType.isEmpty()) {
            return null
        } else {
            File dir = classesDirsOfType.singleFile
            return dir.isDirectory() && !dir.toPath().isEmpty() ? dir : null
        }
    }

    List<String> createGatlingArgs() {

        FileCollection classesDirs = project.sourceSets.gatling.output.classesDirs

        File javaClasses = classesDirForLanguage(classesDirs, 'java')
        File scalaClasses = classesDirForLanguage(classesDirs, 'scala')
        File kotlinClasses = classesDirForLanguage(classesDirs, 'kotlin')
        File binariesFolder = scalaClasses != null ? scalaClasses :
            kotlinClasses != null ? kotlinClasses : javaClasses

        return ['-bf', binariesFolder.absolutePath,
                "-rsf", "${project.sourceSets.gatling.output.resourcesDir}",
                "-rf", gatlingReportDir.absolutePath]
    }

    Iterable<String> simulationFilesToFQN() {
        def javaSrcDirs = project.sourceSets.gatling.java.srcDirs.collect { Paths.get(it.absolutePath) }
        def javaFiles = getJavaSimulationSources().collect { Paths.get(it.absolutePath) }

        def javaFQNs = javaFiles.collect { Path srcFile ->
            javaSrcDirs.find { srcFile.startsWith(it) }.relativize(srcFile).join(".") - ".java"
        }

        List<String> kotlinFQNs
        if (project.sourceSets.gatling.hasProperty("kotlin")) {
            def kotlinSrcDirs = project.sourceSets.gatling.kotlin.srcDirs.collect { Paths.get(it.absolutePath) }
            def kotlinFiles = getKotlinSimulationSources().collect { Paths.get(it.absolutePath) }

            kotlinFQNs = kotlinFiles.collect { Path srcFile ->
                kotlinSrcDirs.find { srcFile.startsWith(it) }.relativize(srcFile).join(".") - ".kt"
            }
        } else {
            kotlinFQNs = []
        }

        def scalaSrcDirs = project.sourceSets.gatling.scala.srcDirs.collect { Paths.get(it.absolutePath) }
        def scalaFiles = getScalaSimulationSources().collect { Paths.get(it.absolutePath) }

        def scalaFQNs = scalaFiles.collect { Path srcFile ->
            scalaSrcDirs.find { srcFile.startsWith(it) }.relativize(srcFile).join(".") - ".scala"
        }

        return javaFQNs + kotlinFQNs + scalaFQNs
    }

    @TaskAction
    void gatlingRun() {
        def gatlingExt = project.extensions.getByType(GatlingPluginExtension)

        Map<String, ExecResult> results = simulationFilesToFQN().collectEntries { String simulationClzName ->
            [(simulationClzName): project.javaexec({ JavaExecSpec exec ->
                exec.main = GatlingPluginExtension.GATLING_MAIN_CLASS
                exec.classpath = project.configurations.gatlingRuntimeClasspath

                exec.jvmArgs this.jvmArgs ?: gatlingExt.jvmArgs
                exec.systemProperties System.properties
                exec.systemProperties this.systemProperties ?: gatlingExt.systemProperties
                exec.environment += gatlingExt.environment
                exec.environment += this.environment

                def logbackFile = LogbackConfigTask.logbackFile(project.buildDir)
                if (logbackFile.exists()) {
                    exec.systemProperty("logback.configurationFile", logbackFile.absolutePath)
                }

                exec.args this.createGatlingArgs()
                exec.args "-s", simulationClzName

                exec.standardInput = System.in

                exec.ignoreExitValue = true
            } as Action<JavaExecSpec>)]
        }

        Map<String, ExecResult> failed = results.findAll { it.value.exitValue != 0 }

        if (!failed.isEmpty()) {
            throw new TaskExecutionException(this, new RuntimeException("There're failed simulations: ${failed.keySet().sort().join(", ")}"))
        }
    }
}
