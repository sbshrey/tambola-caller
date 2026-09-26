buildscript {
    dependencies {
        constraints {
            // Keep these on the build classpath; app/service dependencies are reviewed separately.
            listOf(
                "org.apache.commons:commons-lang3:3.20.0",
                "org.bitbucket.b_c:jose4j:0.9.6",
                "org.bouncycastle:bcprov-jdk18on:1.85",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.bouncycastle:bcutil-jdk18on:1.85",
                "org.jdom:jdom2:2.0.6.1",
            ).forEach { coordinate ->
                add("classpath", coordinate) { because("Build dependency security review: BUILD_TOOL_REVIEW.md") }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.legacy.kapt) apply false
}
