import java.time.Duration

plugins { application; alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(17) }
application { mainClass.set("io.github.sbshrey.tambola.server.ServerKt") }
dependencies {
    implementation("com.google.crypto.tink:apps-rewardedads:1.14.0")
    // Align every transitive Netty module with the HTTP/security fixes newer than Ktor's baseline.
    implementation(platform(libs.netty.bom))
    implementation(project(":domain")); implementation(project(":protocol")); implementation(libs.serialization.json)
    implementation(libs.ktor.server.core); implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets); implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages); implementation(libs.ktor.json)
    implementation(libs.postgres); implementation(libs.hikari); implementation(libs.logback)
    testImplementation(libs.junit); testImplementation(libs.ktor.test.host)
    testImplementation(project(":client"))
    testImplementation(libs.ktor.client.content.negotiation); testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.client.okhttp)
}
tasks.test {
    maxHeapSize = "1024m"
    // Integration tests require an explicitly supplied isolated PostgreSQL database.
    environment("TAMBOLA_DATABASE_URL", System.getenv("TAMBOLA_TEST_DATABASE_URL") ?: "")
    environment("TAMBOLA_DATABASE_USER", System.getenv("TAMBOLA_TEST_DATABASE_USER") ?: "")
    environment("TAMBOLA_DATABASE_PASSWORD", System.getenv("TAMBOLA_TEST_DATABASE_PASSWORD") ?: "")
}

tasks.register<JavaExec>("bingoAndroid") {
    dependsOn(tasks.testClasses)
    if (rootProject.findProject(":app") != null) dependsOn(":app:assembleDebug", ":app:assembleDebugAndroidTest")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.sbshrey.tambola.server.BingoAndroidFixture")
    workingDir = rootProject.projectDir
    maxHeapSize = "512m"
    timeout.set(Duration.ofMinutes(4))
    environment("TAMBOLA_DATABASE_URL", System.getenv("TAMBOLA_TEST_DATABASE_URL") ?: "")
    environment("TAMBOLA_DATABASE_USER", System.getenv("TAMBOLA_TEST_DATABASE_USER") ?: "")
    environment("TAMBOLA_DATABASE_PASSWORD", System.getenv("TAMBOLA_TEST_DATABASE_PASSWORD") ?: "")
}

// Explicit native/real-PostgreSQL refill fixture; never packaged into the service.
tasks.register<JavaExec>("coinRefillAndroid") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.sbshrey.tambola.server.CoinRefillAndroid")
    workingDir = rootProject.projectDir
    maxHeapSize = "512m"
    jvmArgs("-XX:ActiveProcessorCount=4")
    timeout.set(Duration.ofMinutes(4))
    environment("TAMBOLA_DATABASE_URL", System.getenv("TAMBOLA_TEST_DATABASE_URL") ?: "")
    environment("TAMBOLA_DATABASE_USER", System.getenv("TAMBOLA_TEST_DATABASE_USER") ?: "")
    environment("TAMBOLA_DATABASE_PASSWORD", System.getenv("TAMBOLA_TEST_DATABASE_PASSWORD") ?: "")
}

tasks.register<JavaExec>("coinGameLoad") {
    dependsOn(tasks.testClasses, tasks.installDist)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.sbshrey.tambola.server.CoinGameLoad")
    workingDir = rootProject.projectDir
    maxHeapSize = "1024m"
    jvmArgs("-XX:ActiveProcessorCount=8")
    timeout.set(Duration.ofMinutes(if ((System.getenv("TAMBOLA_COIN_LOAD_FIRST_COHORT")?.toIntOrNull() ?: 0) > 0) 16 else 12))
}

tasks.register<JavaExec>("coinPurchaseBurst") {
    dependsOn(tasks.testClasses, tasks.installDist)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.sbshrey.tambola.server.CoinPurchaseBurst")
    workingDir = rootProject.projectDir
    maxHeapSize = "1024m"
    jvmArgs("-XX:ActiveProcessorCount=8")
    timeout.set(Duration.ofMinutes(3))
}

// Opt-in real-process capacity fixture. Never part of the ordinary unit-test task.
tasks.register<JavaExec>("loadTest") {
    dependsOn(tasks.testClasses, tasks.installDist)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.sbshrey.tambola.server.ServiceLoad")
    workingDir = rootProject.projectDir
    maxHeapSize = "768m"
}

tasks.register<JavaExec>("historyDeletionLoad") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.sbshrey.tambola.server.HistoryDeletionLoad")
    workingDir = rootProject.projectDir
    maxHeapSize = "128m"
    jvmArgs("-XX:ActiveProcessorCount=4")
    timeout.set(Duration.ofMinutes(5))
    environment("TAMBOLA_DATABASE_URL", System.getenv("TAMBOLA_TEST_DATABASE_URL") ?: "")
    environment("TAMBOLA_DATABASE_USER", System.getenv("TAMBOLA_TEST_DATABASE_USER") ?: "")
    environment("TAMBOLA_DATABASE_PASSWORD", System.getenv("TAMBOLA_TEST_DATABASE_PASSWORD") ?: "")
}

// Full 90-call automatic game with real process/network interruptions; opt-in, about ten minutes.
tasks.register<JavaExec>("automaticRecoveryLoad") {
    dependsOn(tasks.testClasses, tasks.installDist)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.sbshrey.tambola.server.AutomaticRecoveryLoad")
    workingDir = rootProject.projectDir
    maxHeapSize = "512m"
    jvmArgs("-XX:ActiveProcessorCount=4")
    timeout.set(Duration.ofMinutes(20))
}

// Nine automatic rematches with persistent clients; opt-in, roughly seventy minutes.
tasks.register<JavaExec>("automaticSoakLoad") {
    dependsOn(tasks.testClasses, tasks.installDist)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.sbshrey.tambola.server.AutomaticSoakLoad")
    workingDir = rootProject.projectDir
    maxHeapSize = "768m"
    jvmArgs("-XX:ActiveProcessorCount=4")
    // The fixture's 85-minute deadline runs cleanup before this outer watchdog.
    timeout.set(Duration.ofMinutes(90))
}
