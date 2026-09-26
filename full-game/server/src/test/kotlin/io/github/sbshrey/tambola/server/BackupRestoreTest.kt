package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BackupRestoreTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()

    @Test fun `actual primary backup restore cannot resurrect deleted access or shared profile fields after replay`() {
        val journal = DeletionJournal(additionalDatabase()).also { it.migrate() }
        service = RoomService(database, now::get, journal)
        service.replayDeletions()
        val host = service.register(GuestRequest("Restore Asha", 6), id())
        val peer = service.register(GuestRequest("Restore Bina", 3), id())
        val room = service.create(host.token, CreateRoomRequest(id(), RoomOptions(automaticCalling = false))).snapshot
        service.join(peer.token, room.code)
        fun command(actor: GuestCredentials, action: RoomAction) = service.command(actor.token, room.code,
            CommandRequest(id(), service.read(actor.token, room.code).snapshot.revision, action))
        command(host, RoomAction.Ready(true)); command(peer, RoomAction.Ready(true)); command(host, RoomAction.Start)
        command(host, RoomAction.Draw); command(host, RoomAction.End); command(host, RoomAction.Rematch)
        val peerRequest = CommandRequest(id(), service.read(peer.token, room.code).snapshot.revision, RoomAction.Ready(true))
        service.command(peer.token, room.code, peerRequest)
        command(host, RoomAction.Ready(true)); command(host, RoomAction.Start); command(host, RoomAction.Draw)
        val before = service.read(peer.token, room.code).snapshot
        val deletion = DeleteProfileRequest(id())
        withPrimaryBackup { restore ->
            val receipt = service.deleteProfile(host.token, deletion, "restore-host")
            val later = service.register(GuestRequest("Created after backup"), id())
            val laterRequest = DeleteProfileRequest(id())
            val laterReceipt = service.deleteProfile(later.token, laterRequest, "restore-later")
            assertEquals(2L, journal.position().head)
            restore()
            // Positive control: the old primary snapshot really contains accessible unredacted data.
            val unsafe = RoomService(database, now::get)
            assertEquals("Restore Asha", unsafe.read(host.token, room.code).snapshot.members.first { it.playerId == host.playerId }.displayName)
            service = RoomService(database, now::get, journal)
            assertEquals(401, assertThrows(ApiFailure::class.java) { service.read(host.token, room.code) }.status)
            assertEquals(2, service.replayDeletions())
            assertEquals(receipt, service.deleteProfile(host.token, deletion, "confirm-restored"))
            assertEquals(laterReceipt, service.deleteProfile(later.token, laterRequest, "confirm-newer"))
            val after = service.read(peer.token, room.code).snapshot
            assertEquals(peer.playerId, after.hostId)
            assertEquals(before.round!!.called, after.round!!.called)
            assertEquals(before.round!!.ownTickets, after.round!!.ownTickets)
            assertEquals(before.round!!.drawCommitment, after.round!!.drawCommitment)
            assertEquals(before.round!!.scores, after.round!!.scores)
            assertEquals("Deleted player", after.round!!.players.first { it.id == host.playerId }.name)
            assertEquals(0, after.round!!.players.first { it.id == host.playerId }.avatar)
            assertEquals(3, after.round!!.players.first { it.id == peer.playerId }.avatar)
            val persisted = database.transaction { it.query("SELECT payload FROM rooms UNION ALL SELECT payload FROM finished_rounds UNION ALL SELECT response FROM command_receipts") { row -> row.getString(1) }.joinToString() }
            assertFalse(persisted.contains("Restore Asha"))
            assertTrue(persisted.contains("Restore Bina"))
            assertTrue(service.command(peer.token, room.code, peerRequest).snapshot.members.filter { it.playerId == host.playerId }.all { it.displayName == "Deleted player" && it.avatar == 0 })
            assertEquals(0, service.replayDeletions())
            assertEquals(after.revision, service.read(peer.token, room.code).snapshot.revision)
            assertEquals(2, command(peer, RoomAction.Draw).snapshot.round!!.called.size)
        }
    }
}
