pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "TambolaTogether"
include(":domain", ":protocol", ":server")
if (!providers.gradleProperty("serverOnly").map(String::toBoolean).getOrElse(false)) include(":app")
