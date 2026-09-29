package io.github.sbshrey.tambola.domain

private val personaFirstNames = listOf("Aarav", "Mira", "Kabir", "Noor", "Dev", "Tara", "Rohan", "Isha",
    "Arjun", "Leela", "Neel", "Anaya", "Vihaan", "Diya", "Samar", "Riya")
private val personaLastNames = listOf("Mehta", "Rao", "Shah", "Kapoor", "Sen", "Nair", "Sethi", "Patel",
    "Joshi", "Bose", "Suri", "Verma", "Bhat", "Das", "Sinha", "Rana")

/** Fictional names, not real accounts. The computer flag remains authoritative. */
fun practicePersona(roomId: String, index: Int): Player {
    require(index in 1..49)
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest("$roomId:personas".toByteArray(Charsets.UTF_8))
    // Odd stride permutes all 256 combinations: no collisions within a 49-opponent roster.
    val combination = ((bytes[0].toInt() and 255) + (index - 1) * 37) % 256
    val name = "${personaFirstNames[combination % 16]} ${personaLastNames[combination / 16]}"
    val avatar = Math.floorMod(roomId.hashCode() + index - 1, AVATAR_COUNT)
    return Player("computer-$roomId-$index", name, computer = true, avatar = avatar)
}
