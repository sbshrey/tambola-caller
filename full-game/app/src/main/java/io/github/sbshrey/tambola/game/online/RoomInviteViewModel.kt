package io.github.sbshrey.tambola.game.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.github.sbshrey.tambola.game.BuildConfig
import io.github.sbshrey.tambola.protocol.RoomInvites
import io.github.sbshrey.tambola.protocol.FriendInvites
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RoomInviteState(val code: String? = null, val navigate: Boolean = false, val revision: Int = 0,
    val friendTable: Boolean = BuildConfig.ROOM_DISCOVERY_URL.isNotEmpty())

/** Only the bounded code is saved. An empty code represents an invalid invitation; no raw URI is retained. */
class RoomInviteViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(RoomInviteState(savedState["room_invite"], savedState.contains("room_invite")))
    val state = mutable.asStateFlow()

    fun receive(uri: String?) {
        val code = (if (BuildConfig.ROOM_DISCOVERY_URL.isNotEmpty()) FriendInvites.parse(uri)
            else RoomInvites.parse(uri, BuildConfig.ROOM_API_URL, BuildConfig.DEBUG)).orEmpty()
        savedState["room_invite"] = code
        mutable.value = RoomInviteState(code, navigate = true, revision = mutable.value.revision + 1)
    }

    fun navigated() { mutable.value = mutable.value.copy(navigate = false) }
    fun dismiss() {
        savedState.remove<String>("room_invite")
        mutable.value = mutable.value.copy(code = null, navigate = false)
    }
}
