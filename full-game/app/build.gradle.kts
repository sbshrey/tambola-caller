import java.security.MessageDigest
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "io.github.sbshrey.tambola.game"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.sbshrey.tambola.game"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0-alpha05"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        val endpoint = providers.gradleProperty("tambolaApiUrl").getOrElse("")
        require(endpoint.isEmpty() || (URI(endpoint).let { it.scheme == "https" && it.host != null && it.userInfo == null && it.query == null && it.fragment == null && it.path.orEmpty() in listOf("", "/") }))
        debug { buildConfigField("String", "ROOM_API_URL", "\"${endpoint.ifEmpty { "http://127.0.0.1:8080" }}\"") }
        release {
            buildConfigField("String", "ROOM_API_URL", "\"$endpoint\"")
            isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/voiceAssets"))
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

val prepareVoices by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("generated/voiceAssets/voices"))
    mapOf("en" to "../audio", "hi" to "../audio/hi", "hinglish" to "../audio/hinglish").forEach { (lang, path) ->
        from(rootProject.file(path)) { include("manifest.json", "numbers/*.mp3"); into(lang) }
    }
    doLast {
        listOf("en", "hi", "hinglish").forEach { lang ->
            val folder = layout.buildDirectory.dir("generated/voiceAssets/voices/$lang").get().asFile
            val manifest = groovy.json.JsonSlurper().parse(folder.resolve("manifest.json")) as Map<*, *>
            val clips = manifest["clips"] as Map<*, *>
            (1..90).forEach { number ->
                val clip = clips[number.toString()] as? Map<*, *> ?: error("Missing voice metadata: $lang/$number")
                val file = folder.resolve("numbers/%02d.mp3".format(number))
                check(file.exists()) { "Missing voice: $lang/$number" }
                val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                check(hash == clip["sha256"]) { "Changed voice: $lang/$number" }
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(prepareVoices) }
dependencies {
    implementation(project(":domain"))
    implementation(project(":protocol"))
    implementation(project(":client"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.foundation); implementation(libs.compose.material3)
    implementation(libs.compose.preview); debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose); implementation(libs.lifecycle.compose); implementation(libs.lifecycle.viewmodel)
    implementation(libs.serialization.json); implementation(libs.coroutines.android)
    implementation(libs.room.runtime); kapt(libs.room.compiler); implementation(libs.datastore)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.runner); debugImplementation(libs.compose.test.manifest)
}
