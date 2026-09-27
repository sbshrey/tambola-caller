package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.FriendInvites
import org.junit.Assert.*
import org.junit.Test

class FriendInvitesTest {
    @Test fun invitationCarriesOnlyACanonicalCode() {
        assertEquals("ABCDEFG2", FriendInvites.parse("tambola-beta://friends/ABCDEFG2"))
        for (uri in listOf(null, "", "tambola-beta://friends/abcdefg2", "tambola-beta://friends/ABCDEFG0",
            "tambola-beta://friends/ＡBCDEFG2", "tambola-beta://friends/%41BCDEFG2", "tambola-beta://friends/ABCDEFG2/",
            "tambola-beta://friends/ABCDEFG2?server=https://evil.example", "tambola-beta://friends/ABCDEFG2#token",
            "tambola-beta://friends:443/ABCDEFG2", "tambola-beta://user@friends/ABCDEFG2", "tambola-beta://evil/ABCDEFG2",
            "https://friends/ABCDEFG2", "intent://friends/ABCDEFG2", " tambola-beta://friends/ABCDEFG2", "x".repeat(10000))) {
            assertNull(uri, FriendInvites.parse(uri))
        }
    }
}
