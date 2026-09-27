package io.github.sbshrey.tambola.protocol

/** A public-beta invitation selects only a code, never an endpoint, identity or purchase. */
object FriendInvites {
    private val invitation = Regex("tambola-beta://friends/([A-HJ-NP-Z2-9]{8})")
    fun parse(uri: String?): String? = uri?.takeIf { it.length <= 64 }?.let {
        invitation.matchEntire(it)?.groupValues?.get(1)
    }
}
