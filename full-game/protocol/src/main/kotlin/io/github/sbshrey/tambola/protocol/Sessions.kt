package io.github.sbshrey.tambola.protocol

import kotlinx.serialization.Serializable

/** Device proof is used only to renew sessions, never as a game bearer token. */
@Serializable data class EnrollDeviceRequest(val deviceKey: String) {
    override fun toString() = "EnrollDeviceRequest(redacted)"
}
@Serializable data class DeviceEnrollment(val playerId: String, val revision: Long)

/** The client persists this random token before sending so a lost response is replayable.
 * Revision prevents an older renewal from replacing a subsequently issued session. */
@Serializable data class RenewSessionRequest(val expectedRevision: Long, val token: String) {
    override fun toString() = "RenewSessionRequest(revision=$expectedRevision, token=redacted)"
}
@Serializable data class RenewedSession(val credentials: GuestCredentials, val revision: Long) {
    override fun toString() = "RenewedSession(revision=$revision, credentials=redacted)"
}
