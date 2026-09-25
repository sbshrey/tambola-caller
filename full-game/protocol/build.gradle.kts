plugins { `java-library`; alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(17) }
dependencies { api(project(":domain")); implementation(libs.serialization.json) }
