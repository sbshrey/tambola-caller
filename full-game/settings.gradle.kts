pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "TambolaTogether"
include(":domain", ":protocol", ":client", ":server")
if (!providers.gradleProperty("serverOnly").map(String::toBoolean).getOrElse(false)) {
    include(":app")
    if (providers.gradleProperty("tambolaBenchmarks").map(String::toBoolean).getOrElse(false)) include(":macrobenchmark")
}
