package io.github.sbshrey.tambola.client

import io.github.sbshrey.tambola.protocol.RoomInvites
import org.junit.Assert.*
import org.junit.Test

class RoomInvitesTest {
    @Test fun canonicalLinksKeepTheConfiguredServiceAndAcceptOnlyItsExactOrigin() {
        val origin = "https://rooms.example"
        val code = "ABCDEFG2"
        assertEquals("$origin/invite/$code", RoomInvites.link("$origin/", code.lowercase()))
        for (link in listOf("$origin/invite/$code", "$origin:443/invite/abcdefg2", "https://ROOMS.EXAMPLE/invite/$code", "http://rooms.example/invite/$code"))
            assertEquals(link, code, RoomInvites.parse(link, origin))
        assertEquals(code, RoomInvites.parse("https://rooms.example:8443/invite/$code", "https://rooms.example:8443"))
        assertNull(RoomInvites.parse("http://rooms.example/invite/$code", "https://rooms.example:8443"))
        assertNull(RoomInvites.parse("https://rooms.example/invite/$code", "https://rooms.example:8443"))
    }

    @Test fun ambiguousAndForeignInputsCannotChangeTheEndpointOrSmugglePayloads() {
        val origin = "https://rooms.example"
        for (input in listOf(null, "", " $origin/invite/ABCDEFG2", "$origin/invite/ABCDEFG2 ",
            "https://evil.example/invite/ABCDEFG2", "https://rooms.example.evil/invite/ABCDEFG2",
            "https://rooms.example@evil.example/invite/ABCDEFG2", "https://evil@rooms.example/invite/ABCDEFG2",
            "$origin:444/invite/ABCDEFG2", "$origin/invite/ABCDEFG2?server=https://evil.example", "$origin/invite/ABCDEFG2#token",
            "$origin/invite/ABCDEFG2/", "$origin/invite/%41BCDEFG2", "$origin/invite/../ABCDEFG2", "$origin/invite/ABCDEFG0",
            "$origin/invite/ABCDIEFG", "$origin/invite/ＡBCDEFG2", "$origin/invite/ABCDEFG23", "$origin/other/ABCDEFG2",
            "file:///invite/ABCDEFG2", "javascript:ABCDEFG2", "intent://rooms.example/invite/ABCDEFG2", "/invite/ABCDEFG2", "x".repeat(513))) {
            assertNull(input, RoomInvites.parse(input, origin))
        }
    }

    @Test fun localHttpRequiresTheExplicitLoopbackDevelopmentException() {
        val origin = "http://127.0.0.1:8080"
        assertNull(RoomInvites.origin(origin))
        assertEquals("ABCDEFG2", RoomInvites.parse("$origin/invite/ABCDEFG2", origin, true))
        assertNull(RoomInvites.parse("http://127.0.0.1:8081/invite/ABCDEFG2", origin, true))
        assertNull(RoomInvites.origin("http://rooms.example", true))
        assertNull(RoomInvites.origin("http://localhost:8080", true))
        assertNull(RoomInvites.parse("$origin/invite/ABCDEFG2", "", true))
        for (invalid in listOf("https://user@rooms.example", "https://rooms.example/api", "https://rooms.example?x=1", "https://rooms.example#x", "https://rooms.example:0", "https://rooms.example:65536"))
            assertNull(invalid, RoomInvites.origin(invalid))
    }
}
