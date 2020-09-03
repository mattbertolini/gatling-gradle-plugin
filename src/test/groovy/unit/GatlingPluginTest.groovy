package unit

import io.gatling.gradle.GatlingPluginExtension
import io.gatling.gradle.GatlingRunTask
import io.gatling.gradle.LogbackConfigTaskAction
import helper.GatlingUnitSpec
import org.gradle.api.Task
import org.gradle.language.jvm.tasks.ProcessResources

class GatlingPluginTest extends GatlingUnitSpec {

    def "should create gatling configurations"() {
        expect:
        ['gatling', 'gatlingCompile', 'gatlingRuntime', 'gatlingImplementation', 'gatlingRuntimeOnly'].every {
            project.configurations.getByName(it) != null
        }
    }

    def "should create gatling extension for project "() {
        expect:
        with(gatlingExt) {
            it instanceof GatlingPluginExtension
            it.simulations == DEFAULT_SIMULATIONS
            it.jvmArgs == DEFAULT_JVM_ARGS
            it.systemProperties == DEFAULT_SYSTEM_PROPS
            it.logLevel == "WARN"
        }
    }

    def "should add gatling dependencies with default version"() {
        when:
        project.evaluate()
        then:
        project.configurations.getByName("gatling").allDependencies.find {
            it.name == "gatling-charts-highcharts" && it.version == GatlingPluginExtension.GATLING_TOOL_VERSION
        }
        project.configurations.getByName("gatlingImplementation").allDependencies.find {
            it.name == "scala-library" && it.version == GatlingPluginExtension.SCALA_VERSION
        }
        project.configurations.getByName("implementation").allDependencies.find {
            it.name == "scala-library" && it.version == GatlingPluginExtension.SCALA_VERSION
        }
    }

    def "should allow overriding gatling version via extension"() {
        when:
        project.gatling { toolVersion = '3.0.0-RC1' }
        and:
        project.evaluate()
        then:
        project.configurations.getByName("gatling").allDependencies.find {
            it.name == "gatling-charts-highcharts" && it.version == "3.0.0-RC1"
        }
    }

    def "should allow overriding scala version via extension"() {
        when:
        project.gatling { scalaVersion = '2.11.3' }
        and:
        project.evaluate()
        then:
        project.configurations.getByName("gatlingImplementation").allDependencies.find {
            it.name == "scala-library" && it.version == "2.11.3"
        }
        project.configurations.getByName("implementation").allDependencies.find {
            it.name == "scala-library" && it.version == "2.11.3"
        }
    }

    def "should create gatlingRun task"() {
        expect:
        with(gatlingRunTask) {
            it instanceof GatlingRunTask
            it.simulations == null
            it.jvmArgs == null
            it.systemProperties == null
        }
    }

    def "should create processGatlingResources task"() {
        expect:
        with(project.tasks.getByName("processGatlingResources")) { Task task ->
            task instanceof ProcessResources
            task.actions.any { it.hasProperty("action") && it.action instanceof LogbackConfigTaskAction }
        }
    }
}
