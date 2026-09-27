plugins { id("com.android.test") }

android {
    namespace = "io.github.sbshrey.tambola.benchmark"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        create("lanRelease") {
            isDebuggable = true // Only the separate driver; the measured app is not debuggable.
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("publicBeta") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}
kotlin { jvmToolchain(17) }
androidComponents.beforeVariants { it.enable = it.buildType in setOf("lanRelease", "publicBeta") }
abstract class BenchmarkTrust : Sync() {
    @get:OutputDirectory abstract val generatedRoot: DirectoryProperty
}
val prepareTrust = tasks.register<BenchmarkTrust>("prepareBenchmarkTrust") {
    dependsOn(":app:prepareLanTrust")
    from(project(":app").layout.buildDirectory.dir("generated/lanTrust"))
    generatedRoot.set(layout.buildDirectory.dir("generated/lanTrust"))
    into(generatedRoot)
    doLast {
        // Perfetto's processor uses a local HTTP socket in the driver process only.
        val config = generatedRoot.file("xml/network_security_config.xml").get().asFile
        config.writeText(config.readText().replace("</network-security-config>", """
            <domain-config cleartextTrafficPermitted="true">
                <domain>localhost</domain><domain>127.0.0.1</domain>
            </domain-config>
            </network-security-config>
        """.trimIndent()))
    }
}
androidComponents.onVariants { variant ->
    if (variant.buildType == "lanRelease") variant.sources.res?.addGeneratedSourceDirectory(prepareTrust) { it.generatedRoot }
}
dependencies {
    implementation(libs.macrobenchmark)
    implementation(libs.uiautomator)
    implementation(libs.android.test.runner)
    implementation(project(":client"))
    implementation(project(":domain"))
    implementation(libs.coroutines.android)
}
