package configurations

import common.buildToolGradleParameters
import common.customGradle
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import model.CIBuildModel
import model.Stage

class Gradleception(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id("${model.projectId}_Gradleception")
    name = "Gradleception - Java8 Linux"
    description = "Builds Gradle with the version of Gradle which is currently under development (twice)"

    features {
        publishBuildStatusToGithub(model)
    }

    failureConditions {
        javaCrash = false
    }

    params {
        // Override the default commit id so the build steps produce reproducible distribution
        param("env.BUILD_COMMIT_ID", "HEAD")
    }

    // So that the build steps produces reproducible distribution
    val magicTimestamp = "20210102030405+0000"
    val buildScanTagForType = buildScanTag("Gradleception")
    val defaultParameters = (buildToolGradleParameters() + listOf(buildScanTagForType) + "-Porg.gradle.java.installations.auto-download=false").joinToString(separator = " ")

    steps.calculateMd5AndSetEnvStep("CALCULATE_FIRST_DISTRIBUTION_MD5", "%teamcity.build.checkoutDir%", gradleceptionFilePatterns)

    applyDefaults(
        model,
        this,
        ":distributions-full:install",
        notQuick = true,
        extraParameters = "-Pgradle_installPath=dogfood-first -PignoreIncomingBuildReceipt=true -PbuildTimestamp=$magicTimestamp $buildScanTagForType",
        extraSteps = {
            calculateMd5AndSetEnvStep("CALCULATE_SECOND_DISTRIBUTION_MD5", "%teamcity.build.checkoutDir%/dogfood-first")

            localGradle {
                name = "BUILD_WITH_BUILT_GRADLE"
                tasks = "clean :distributions-full:install"
                gradleHome = "%teamcity.build.checkoutDir%/dogfood-first"
                gradleParams = "-Pgradle_installPath=dogfood-second -PignoreIncomingBuildReceipt=true -PbuildTimestamp=$magicTimestamp $defaultParameters"
            }
            localGradle {
                name = "QUICKCHECK_WITH_GRADLE_BUILT_BY_GRADLE"
                tasks = "clean sanityCheck test"
                gradleHome = "%teamcity.build.checkoutDir%/dogfood-second"
                gradleParams = defaultParameters
            }
        })
})

// Only changes matching these patterns cause Gradleception build to rerun.
val gradleceptionFilePatterns = listOf(
    "./build-logic/*.kts",
    "./build-logic/*/*.kts",
    "./build-logic/*/src/main/**",

    "./build-logic-commons/*.kts",
    "./build-logic-commons/*/*.kts",
    "./build-logic-commons/*/src/main/**",

    "./build-logic-settings/*.kts",
    "./build-logic-settings/*/*.kts",
    "./build-logic-settings/*/src/main/**",

    "./subprojects/*/*.kts",
    "./subprojects/*/*.gradle",
    "./subprojects/*/src/main/**",

    "./*.kts",
    "./gradle/**"
)

// translate Ant pattern to find command's `-regex`
// . -> \.
// * -> [^/]* because single asterisk should not match /
// ** -> .*
fun String.singlePatternToFindCommandRegex(): String {
    val regex = replace("**", "DOUBLE_ASTERISK")
        .replace(".", "SINGLE_DOT")
        .replace("*", "[^/]*")
        .replace("SINGLE_DOT", "\\.")
        .replace("DOUBLE_ASTERISK", ".*")
    return "-regex '$regex'"
}

fun gradleceptionFindCommand(dir: String, patterns: List<String>): String {
    if (patterns.isEmpty()) {
        return "find $dir -type f"
    } else {
        return "find $dir -type f \\( ${patterns.joinToString(" -o ") { it.singlePatternToFindCommandRegex() }} \\)"
    }
}

fun BuildSteps.calculateMd5AndSetEnvStep(stepName: String, dir: String, patterns: List<String> = emptyList()) {
    script {
        name = stepName
        scriptContent = """
                set -x
                FILES=`${gradleceptionFindCommand(dir, patterns)}`
                MD5=`echo ${'$'}FILES | sort | xargs md5sum | md5sum | awk '{ print ${'$'}1 }'`
                echo "##teamcity[setParameter name='env.ORG_GRADLE_PROJECT_versionQualifier' value='gradleception-${'$'}MD5']"
            """.trimIndent()
    }
}

fun BuildSteps.localGradle(init: GradleBuildStep.() -> Unit): GradleBuildStep =
    customGradle(init) {
        param("ui.gradleRunner.gradle.wrapper.useWrapper", "false")
        buildFile = ""
    }
