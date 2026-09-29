package io.github.sbshrey.tambola.domain

/** Fictional practice personas. The public flag must remain visible to players. */
fun practicePersona(roomId: String, index: Int): Player {
    require(index in 1..49)
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest("$roomId:practice:$index".toByteArray(Charsets.UTF_8))
    val number = ((bytes[0].toInt() and 255) shl 16) or ((bytes[1].toInt() and 255) shl 8) or (bytes[2].toInt() and 255)
    val avatar = Math.floorMod(roomId.hashCode() + index - 1, AVATAR_COUNT)
    return Player("computer-$roomId-$index", "Player ${100_000 + number % 900_000}", computer = true, avatar = avatar)
}
