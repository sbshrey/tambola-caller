package io.github.sbshrey.tambola.game

/** Debug builds report code locations only: exception messages can contain private saved data. */
internal fun reportStorageFailure(operation: String, error: Throwable) {
    if (!BuildConfig.DEBUG) return
    val locations = generateSequence(error) { it.cause }.take(3).joinToString("\nCaused by ") { cause ->
        cause.javaClass.name + cause.stackTrace.take(12).joinToString("\n", prefix = "\n") { "at $it" }
    }
    android.util.Log.e("TambolaStorage", "$operation failed: $locations")
}
