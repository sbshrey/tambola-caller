package io.github.sbshrey.tambola.game

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sbshrey.tambola.client.HttpRoomApi
import io.github.sbshrey.tambola.domain.GameMode
import io.github.sbshrey.tambola.game.data.PreferenceStore
import io.github.sbshrey.tambola.game.data.Preferences
import io.github.sbshrey.tambola.game.online.*
import io.github.sbshrey.tambola.game.ui.GameText
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Explicit emulator-only integrations with the isolated service, including real ACTION_VIEW delivery. */
class RoomInviteTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val online get() = ViewModelProvider(compose.activity)[OnlineViewModel::class.java]
    private val game get() = ViewModelProvider(compose.activity)[GameViewModel::class.java]
    private val invitations get() = ViewModelProvider(compose.activity)[RoomInviteViewModel::class.java]
    private fun words() = GameText(compose.activity.resources)
    private fun until(predicate: () -> Boolean) = compose.waitUntil(20_000, predicate)
    private fun tap(resource: Int) = compose.tapText(words()(resource))
    private fun connected() = until { online.state.value.connection == Connection.LIVE }
    private fun id() = UUID.randomUUID().toString()
    private fun link(code: String) = RoomInvites.link(BuildConfig.ROOM_API_URL, code, true)
    private fun open(uri: String) {
        val revision = invitations.state.value.revision
        compose.runOnUiThread { compose.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri), compose.activity, MainActivity::class.java).addCategory(Intent.CATEGORY_BROWSABLE)) }
        until { invitations.state.value.revision > revision && game.state.value.screen == Screen.ONLINE }
        compose.waitForIdle()
    }
    private fun language(tag: String) {
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
        until { compose.activity.resources.configuration.locales[0].language == tag }
    }
    @Before fun prepare() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaOnline") == "true")
        check(BuildConfig.DEBUG && BuildConfig.ROOM_API_URL == "http://127.0.0.1:8080" && isAndroidEmulator())
        language("en")
        PreferenceStore(context).update(Preferences(voice = false, effects = false, reducedMotion = true, tutorialCompleted = true))
        until { !online.state.value.loading && !game.state.value.loading }
        compose.runOnIdle { invitations.dismiss(); online.resetLocalData(); game.navigate(Screen.HOME) }
        until { online.state.value.name == null && !online.state.value.loading }
    }
    @After fun resetLanguage() {
        // Persist the English fixture baseline while an Activity can apply it; closing first can leave
        // AppCompat's saved Hindi locale for a separately launched instrumentation process.
        language("en")
        compose.activityRule.scenario.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
    }

    @Test fun invitationSurvivesLanguageAndRecreationAndRequiresExplicitRegistrationAndJoin() = runBlocking<Unit> {
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        try {
            val host = api.guest(GuestRequest("Invite host Bina"))
            val room = api.create(host.token, CreateRoomRequest(id())).snapshot
            open(link(room.code))
            compose.onNodeWithTag("invite-code").assertTextEquals(room.code).assertIsDisplayed()
            assertNull(online.state.value.name); assertNull(OnlineStore(context).read())
            captureTestScreen("invite-en-before-profile")
            compose.activityRule.scenario.recreate()
            assertEquals(room.code, invitations.state.value.code)
            language("hi")
            compose.onNodeWithTag("invite-code").performScrollTo().assertTextEquals(room.code)
            captureTestScreen("invite-hi-before-profile")
            compose.onNodeWithTag("online-name").performScrollTo().performTextReplacement("निमंत्रण आशा")
            compose.onNodeWithTag("online-name").performImeAction()
            compose.tapText(words()(R.string.ui_continue_online))
            until { online.state.value.name != null && !online.state.value.busy }
            assertNull(online.state.value.room)
            assertEquals(1, api.read(host.token, room.code).snapshot.members.size)
            tap(R.string.invite_join); until { online.state.value.room?.code == room.code && !online.state.value.busy }; connected()
            assertEquals(2, api.read(host.token, room.code).snapshot.members.size)
            compose.onNodeWithTag("invite-code").performScrollTo(); compose.waitForIdle(); captureTestScreen("invite-hi-joined")
            val revision = online.state.value.room!!.revision
            open(link(room.code))
            assertEquals(revision, online.state.value.room!!.revision)
            tap(R.string.invite_view_room); assertNull(invitations.state.value.code)
            compose.activityRule.scenario.recreate(); assertNull(invitations.state.value.code)
        } finally { api.close() }
    }

    @Test fun foreignAndMissingInvitationsPreserveOfflineRoundAndExistingProfile() = runBlocking<Unit> {
        compose.runOnIdle { game.setup(GameMode.PRACTICE); game.create() }
        until { !game.state.value.saving && game.state.value.screen == Screen.GAME }
        val original = game.state.value.round!!
        open("https://foreign.example/invite/ABCDEFGH?token=do-not-use")
        compose.onNodeWithText(words()(R.string.invite_invalid)).assertIsDisplayed()
        assertEquals("", invitations.state.value.code); assertNull(online.state.value.name)
        captureTestScreen("invite-invalid")
        tap(R.string.invite_dismiss)
        compose.runOnIdle { online.register("Existing invite Asha", 2) }
        until { online.state.value.playerId != null && !online.state.value.busy }
        val originalProfile = OnlineStore(context).read()!!.credentials
        open(link("ZZZZZZZZ"))
        assertNull(online.state.value.room)
        tap(R.string.invite_join)
        until { online.state.value.error != null && !online.state.value.busy }
        assertFalse(online.state.value.pending); assertNull(online.state.value.room)
        assertEquals(originalProfile, OnlineStore(context).read()!!.credentials)
        assertEquals("ZZZZZZZZ", invitations.state.value.code)
        assertEquals(original.id, game.state.value.round!!.id); assertEquals(original.tickets, game.state.value.round!!.tickets)
        assertEquals(original.called, game.state.value.round!!.called); assertEquals(original.marks, game.state.value.round!!.marks)
    }

    @Test fun activeRoomAndPendingCommandRemainIntactUntilRoundEndsAndPlayerLeaves() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tambolaFaultProxy") == "true")
        suspend fun control(path: String) = withContext(Dispatchers.IO) {
            val connection = java.net.URL("http://127.0.0.1:8082/$path").openConnection() as java.net.HttpURLConnection
            try { connection.requestMethod = "POST"; connection.connectTimeout = 3_000; connection.readTimeout = 3_000; check(connection.responseCode == 200) }
            finally { connection.disconnect() }
        }
        val api = HttpRoomApi(BuildConfig.ROOM_API_URL, true)
        try {
            val otherHost = api.guest(GuestRequest("Other host"))
            val target = api.create(otherHost.token, CreateRoomRequest(id())).snapshot
            compose.runOnIdle { game.navigate(Screen.ONLINE); online.register("Invite current host", 1) }
            until { online.state.value.playerId != null && !online.state.value.busy }
            compose.runOnIdle { online.create(RoomOptions(automaticCalling = false)) }
            until { online.state.value.room != null && !online.state.value.busy }; connected()
            val code = online.state.value.room!!.code
            val peer = api.guest(GuestRequest("Current peer"))
            val joined = api.join(peer.token, code).snapshot
            api.command(peer.token, code, CommandRequest(id(), joined.revision, RoomAction.Ready(true)))
            until { online.state.value.room!!.members.last().ready }
            compose.runOnIdle { online.command(RoomAction.Ready(true)) }
            until { !online.state.value.busy && online.state.value.room!!.members.all { it.ready } }
            compose.runOnIdle { online.command(RoomAction.Start) }
            until { !online.state.value.busy && online.state.value.room!!.phase == RoomPhase.ACTIVE }
            control("arm-command-drop")
            compose.runOnIdle { online.command(RoomAction.Draw) }
            compose.waitUntil(35_000) { online.state.value.pending && !online.state.value.busy && online.state.value.error != null }
            val pending = OnlineStore(context).read()!!.pending
            compose.tapText(words()(R.string.ui_got_it))
            open(link(target.code))
            assertEquals(code, online.state.value.room!!.code); assertEquals(pending, OnlineStore(context).read()!!.pending)
            compose.onNodeWithText(words()(R.string.invite_join)).assertIsNotEnabled()
            compose.onNodeWithText(words()(R.string.ui_leave_room)).assertDoesNotExist()
            captureTestScreen("invite-waits-for-active-room")
            // These controls must remain fully reachable without a fixed footer clipping the
            // invitation at 200% text. Checking the unclipped height catches that regression.
            for (node in listOf(compose.onNodeWithTag("invite-code"),
                compose.onNodeWithText(words()(R.string.invite_other_room, code)),
                compose.onNodeWithText(words()(R.string.invite_join)),
                compose.onNodeWithText(words()(R.string.ui_end_online_round)))) {
                if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()
                node.assertIsDisplayed()
                val full = node.getUnclippedBoundsInRoot()
                val fullHeight = with(compose.density) { (full.bottom - full.top).toPx() }
                assertEquals("The invitation or control is vertically clipped", fullHeight,
                    node.fetchSemanticsNode().boundsInRoot.height, 1f)
            }
            captureTestScreen("invite-active-controls")
            compose.activityRule.scenario.recreate(); assertEquals(target.code, invitations.state.value.code)
            assertEquals(pending, OnlineStore(context).read()!!.pending)
            control("allow-commands")
            compose.tapText(words()(R.string.ui_retry_pending_action))
            until { !online.state.value.busy && !online.state.value.pending }; connected()
            assertEquals(1, online.state.value.room!!.round!!.called.size)
            compose.runOnIdle { online.command(RoomAction.End) }
            until { !online.state.value.busy && online.state.value.room!!.phase == RoomPhase.FINISHED }
            tap(R.string.ui_leave_room)
            compose.onNode(hasText(words()(R.string.ui_leave_room)) and hasAnyAncestor(isDialog())).performClick()
            until { online.state.value.room == null && !online.state.value.busy }
            assertEquals(peer.playerId, api.read(peer.token, code).snapshot.hostId)
            // A valid invitation can become locked after it was sent. Failure must keep the profile and invitation.
            api.command(otherHost.token, target.code, CommandRequest(id(), target.revision, RoomAction.Lock(true)))
            tap(R.string.invite_join); until { !online.state.value.busy && online.state.value.error != null }
            assertNull(online.state.value.room); assertEquals(target.code, invitations.state.value.code)
            compose.tapText(words()(R.string.ui_got_it))
            val locked = api.read(otherHost.token, target.code).snapshot
            api.command(otherHost.token, target.code, CommandRequest(id(), locked.revision, RoomAction.Lock(false)))
            tap(R.string.invite_join); until { !online.state.value.busy && online.state.value.room?.code == target.code }
        } finally { control("allow-commands"); api.close() }
    }
}
