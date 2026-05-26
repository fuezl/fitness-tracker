plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.versions)
}

fun isNonStableVersion(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { keyword ->
        version.uppercase().contains(keyword)
    }
    val stableRegex = "^[0-9,.v-]+(-r)?$".toRegex()
    return !stableKeyword && !stableRegex.matches(version)
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
        isNonStableVersion(candidate.version) && !isNonStableVersion(currentVersion)
    }
    checkForGradleUpdate = true
    outputFormatter = "plain"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

tasks.register("coverageReport") {
    group = "verification"
    description = "Generates debug unit-test coverage reports."
    dependsOn(":app:koverHtmlReportDebug", ":app:koverXmlReportDebug")
}

tasks.register("qualityReports") {
    group = "verification"
    description = "Generates stable coverage and dependency health reports."
    dependsOn("coverageReport", "buildHealth")
}
