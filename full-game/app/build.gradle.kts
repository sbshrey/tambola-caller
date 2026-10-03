import java.security.MessageDigest
import java.util.Locale
import java.net.URI
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.kotlin.serialization)
}
// Account setup is optional for local builds. Public telemetry builds opt in explicitly.
val firebaseConfigured = providers.gradleProperty("tambolaFirebase").map(String::toBoolean).getOrElse(false)
if (firebaseConfigured) {
    require(file("src/publicBeta/google-services.json").isFile) { "Run tools/configure-firebase.ps1 after signing in to Firebase." }
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
}
val adTest = providers.gradleProperty("tambolaAdTest").map(String::toBoolean).getOrElse(false)
// Public identifiers for io.github.sbshrey.tambola.game.beta; never used by debug or Wi-Fi builds.
val adApp = providers.gradleProperty("tambolaAdMobAppId").getOrElse("ca-app-pub-1312548197553464~7883782841")
val adUnit = providers.gradleProperty("tambolaAdMobRewardUnit").getOrElse("ca-app-pub-1312548197553464/9960291000")
// Enable only after the matching unit's reward, consent and SSV settings are verified.
val adLive = providers.gradleProperty("tambolaLiveAds").map(String::toBooleanStrict).getOrElse(false)
require(!adTest || !adLive) { "Choose live public-beta ads or debug test mode, not both" }
require(adApp.matches(Regex("ca-app-pub-[0-9]{16}~[0-9]{10}")) && adUnit.matches(Regex("ca-app-pub-[0-9]{16}/[0-9]{10}")))
require(!adApp.startsWith("ca-app-pub-3940256099942544") && adApp.substringBefore('~') == adUnit.substringBefore('/'))
dependencies {
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
android {
    namespace = "io.github.sbshrey.tambola.game"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.sbshrey.tambola.game"
        minSdk = 26
        targetSdk = 36
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField("boolean", "REWARDED_ADS_ENABLED", "false")
        buildConfigField("boolean", "REWARDED_ADS_TEST", "false")
        buildConfigField("String", "ADMOB_REWARD_UNIT", "\"\"")
        versionCode = 66
        versionName = "0.66.0-alpha66"
        buildConfigField("String", "ROOM_DISCOVERY_URL", "\"\"")
        buildConfigField("boolean", "TELEMETRY_CONFIGURED", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes {
        val endpoint = providers.gradleProperty("tambolaApiUrl").getOrElse("")
        require(endpoint.isEmpty() || (endpoint.length <= 256 && endpoint.all { it.code in 33..126 } && URI(endpoint).let { it.scheme == "https" && it.host != null && it.userInfo == null && it.query == null && it.fragment == null && it.path.orEmpty() in listOf("", "/") && (it.port == -1 || it.port in 1..65535) }))
        debug {
            if (adTest) {
                buildConfigField("boolean", "REWARDED_ADS_ENABLED", "true")
                buildConfigField("boolean", "REWARDED_ADS_TEST", "true")
                buildConfigField("String", "ADMOB_REWARD_UNIT", "\"ca-app-pub-3940256099942544/5224354917\"")
            }
            val origin = endpoint.ifEmpty { "http://127.0.0.1:8080" }
            buildConfigField("String", "ROOM_API_URL", "\"$origin\"")
            manifestPlaceholders["inviteHost"] = URI(origin).host.lowercase(Locale.ROOT)
            manifestPlaceholders["verifyInvites"] = endpoint.isNotEmpty().toString()
        }
        create("lan") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
            versionNameSuffix = "-wifi"
            manifestPlaceholders["verifyInvites"] = "false"
        }
        release {
            buildConfigField("String", "ROOM_API_URL", "\"$endpoint\"")
            manifestPlaceholders["inviteHost"] = if (endpoint.isEmpty()) "disabled.invalid" else URI(endpoint).host.lowercase(Locale.ROOT)
            manifestPlaceholders["verifyInvites"] = endpoint.isNotEmpty().toString()
            isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isShrinkResources = true
        }
        create("lanRelease") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            versionNameSuffix = "-wifi-optimized"
            manifestPlaceholders["verifyInvites"] = "false"
        }
        create("publicBeta") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-internet-beta"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            // Preserve saved-profile identity; transport now uses the permanent publisher hostname.
            manifestPlaceholders["admobAppId"] = adApp
            buildConfigField("String", "ADMOB_REWARD_UNIT", "\"$adUnit\"")
            buildConfigField("boolean", "REWARDED_ADS_ENABLED", adLive.toString())
            buildConfigField("boolean", "TELEMETRY_CONFIGURED", firebaseConfigured.toString())
            buildConfigField("String", "ROOM_API_URL", "\"https://sbshrey.github.io\"")
            buildConfigField("String", "ROOM_DISCOVERY_URL", "\"https://play.thefinxperts.com\"")
            manifestPlaceholders["inviteHost"] = "disabled.invalid"
            manifestPlaceholders["verifyInvites"] = "false"
        }
    }
    androidResources.noCompress += "wav"
    androidResources.localeFilters += listOf("en", "hi")
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
// Generic assemble/build tasks must not require a developer's private host CA.
androidComponents.beforeVariants { variant ->
    if (variant.buildType in setOf("lan", "lanRelease"))
        variant.enable = providers.gradleProperty("tambolaLanCa").isPresent
}

// Lint has its own tool classpath; root buildscript constraints do not reach it.
configurations.matching { it.name == "androidLintTool" }.configureEach {
    listOf(
        "org.apache.commons:commons-lang3:3.20.0",
        "org.apache.httpcomponents:httpclient:4.5.14",
        "org.bouncycastle:bcprov-jdk18on:1.85",
        "org.bouncycastle:bcpkix-jdk18on:1.85",
        "org.bouncycastle:bcutil-jdk18on:1.85",
    ).forEach { coordinate ->
        dependencyConstraints.add(project.dependencies.constraints.create(coordinate) {
            because("Reviewed build-only advisory fixes; see BUILD_TOOL_REVIEW.md")
        })
    }
}

abstract class GeneratedGameAssets : Sync() {
    @get:OutputDirectory abstract val generatedRoot: DirectoryProperty
}
abstract class GeneratedLanTrust : DefaultTask() {
    @get:OutputDirectory abstract val generatedRoot: DirectoryProperty
}

// A separate, explicitly configured Wi-Fi candidate trusts only its host CA for
// that private address. Debug/release builds never inherit this extra trust.
val lanResources = layout.buildDirectory.dir("generated/lanTrust")
val prepareLanTrust = tasks.register<GeneratedLanTrust>("prepareLanTrust") {
    generatedRoot.set(lanResources)
    val origin = providers.gradleProperty("tambolaApiUrl").orElse("")
    val caPath = providers.gradleProperty("tambolaLanCa").orElse("")
    inputs.property("origin", origin)
    if (caPath.get().isNotEmpty()) inputs.file(caPath)
    outputs.dir(lanResources)
    doLast {
        val address = URI(origin.get()).host ?: error("Wi-Fi APK requires -PtambolaApiUrl=https://<private-ip>:8443")
        val octets = address.split('.').map { it.toIntOrNull() ?: -1 }
        require(octets.size == 4 && octets.all { it in 0..255 } &&
            (octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) || (octets[0] == 192 && octets[1] == 168)))
        require(caPath.get().isNotEmpty()) { "Wi-Fi APK requires -PtambolaLanCa=<host root.crt>" }
        val pem = file(caPath.get()).readBytes()
        require(!pem.toString(Charsets.US_ASCII).contains("PRIVATE KEY")) { "Never bundle a private key" }
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.inputStream()) as X509Certificate
        certificate.checkValidity()
        require(certificate.basicConstraints >= 0) { "Wi-Fi trust file must contain a CA certificate" }
        val folder = lanResources.get().asFile
        folder.resolve("raw").mkdirs(); folder.resolve("xml").mkdirs()
        folder.resolve("raw/tambola_lan_ca.pem").writeBytes(pem)
        folder.resolve("xml/network_security_config.xml").writeText("""
            <network-security-config>
                <base-config cleartextTrafficPermitted="false" />
                <domain-config cleartextTrafficPermitted="false">
                    <domain includeSubdomains="false">$address</domain>
                    <trust-anchors><certificates src="@raw/tambola_lan_ca" /></trust-anchors>
                </domain-config>
            </network-security-config>
        """.trimIndent())
    }
}

val prepareVoices = tasks.register<GeneratedGameAssets>("prepareVoices") {
    generatedRoot.set(layout.buildDirectory.dir("generated/voiceAssets"))
    into(generatedRoot.dir("voices"))
    mapOf("en" to "../audio", "hi" to "../audio/hi", "hinglish" to "../audio/hinglish").forEach { (lang, path) ->
        from(rootProject.file(path)) { include("manifest.json", "numbers/*.mp3"); into(lang) }
    }
    doLast {
        listOf("en", "hi", "hinglish").forEach { lang ->
            val folder = generatedRoot.dir("voices/$lang").get().asFile
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
val prepareSounds = tasks.register<GeneratedGameAssets>("prepareSounds") {
    from(rootProject.file("media/sound")) { include("*.wav", "manifest.json") }
    generatedRoot.set(layout.buildDirectory.dir("generated/soundAssets"))
    into(generatedRoot.dir("sound"))
    doLast {
        val folder = destinationDir
        val manifest = groovy.json.JsonSlurper().parse(folder.resolve("manifest.json")) as Map<*, *>
        val clips = manifest["clips"] as Map<*, *>
        val expected = setOf("game-night.wav", "mark.wav", "call.wav", "deal.wav", "win.wav")
        check(clips.keys == expected) { "Incomplete sound manifest" }
        expected.forEach { name ->
            val clip = clips[name] as Map<*, *>
            val bytes = folder.resolve(name).readBytes()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(hash == clip["sha256"] && bytes.size.toLong() == (clip["bytes"] as Number).toLong()) { "Changed sound: $name" }
        }
    }
}
androidComponents.onVariants { variant ->
    if (variant.buildType in setOf("lan", "lanRelease")) variant.sources.res?.addGeneratedSourceDirectory(prepareLanTrust) { it.generatedRoot }
    variant.sources.assets?.addGeneratedSourceDirectory(prepareVoices) { it.generatedRoot }
    variant.sources.assets?.addGeneratedSourceDirectory(prepareSounds) { it.generatedRoot }
}
dependencies {
    constraints {
        add("kapt", "org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}") {
            because("Room's metadata reader must support the selected Kotlin compiler; build-only constraint")
        }
    }
    implementation(libs.appcompat)
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
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.runner); debugImplementation(libs.compose.test.manifest)
}
