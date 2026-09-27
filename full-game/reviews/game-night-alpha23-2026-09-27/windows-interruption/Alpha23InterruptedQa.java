import io.github.sbshrey.tambola.server.Database;
import io.github.sbshrey.tambola.server.DeletionJournal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.HexFormat;

/** One interrupted, independently identified QA run only. Never accepts arbitrary player IDs. */
public class Alpha23InterruptedQa {
  static final String[] IDS = {"248b699f-280e-4512-8aad-1a710b6a3466", "736e1cb3-2a86-4319-9a56-380dce6565e3"};
  static final String[] NAMES = {"Release QA 1", "Player 2604"};
  static final long[] CREATED = {1790486043486L, 1790486044590L};
  static String env(String key) { return System.getenv(key); }
  static void require(boolean value) { if (!value) throw new IllegalStateException("Interrupted QA ownership check failed"); }
  static String proof(String id) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("alpha23-interrupted-qa-2026-09-27\n" + id).getBytes(StandardCharsets.UTF_8)));
  }
  public static void main(String[] args) throws Exception {
    require(args.length == 1 && (args[0].equals("inspect") || args[0].equals("apply")));
    require(env("TAMBOLA_DATABASE_URL").equals("jdbc:postgresql://127.0.0.1:55433/tambola_local"));
    require(env("TAMBOLA_DELETION_DATABASE_URL").equals("jdbc:postgresql://127.0.0.1:55433/tambola_local_journal"));
    try (Connection main = DriverManager.getConnection(env("TAMBOLA_DATABASE_URL"), env("TAMBOLA_DATABASE_USER"), env("TAMBOLA_DATABASE_PASSWORD"));
         Database journalDb = new Database(env("TAMBOLA_DELETION_DATABASE_URL"), env("TAMBOLA_DELETION_DATABASE_USER"), env("TAMBOLA_DELETION_DATABASE_PASSWORD"), "public")) {
      DeletionJournal journal = new DeletionJournal(journalDb);
      journal.verifyMigrations();
      boolean[] present = new boolean[2];
      for (int i = 0; i < 2; i++) {
        try (PreparedStatement query = main.prepareStatement("SELECT g.name, l.created_at, l.amount FROM guests g JOIN coin_ledger l ON l.player_id=g.id AND l.entry_key='starter' WHERE g.id=?")) {
          query.setString(1, IDS[i]);
          try (ResultSet row = query.executeQuery()) {
            present[i] = row.next();
            if (present[i]) { require(NAMES[i].equals(row.getString(1))); require(CREATED[i] == row.getLong(2)); require(row.getLong(3) == 1500); require(!row.next()); }
            else require(journal.find(proof(IDS[i])) != null);
          }
        }
      }
      if (present[0] && present[1]) {
        String sql = "SELECT jsonb_array_length(payload::jsonb->'members') FROM rooms WHERE EXISTS (SELECT 1 FROM jsonb_array_elements(payload::jsonb->'members') m WHERE m->>'id'=?) AND EXISTS (SELECT 1 FROM jsonb_array_elements(payload::jsonb->'members') m WHERE m->>'id'=?)";
        try (PreparedStatement query = main.prepareStatement(sql)) {
          query.setString(1, IDS[0]); query.setString(2, IDS[1]);
          try (ResultSet row = query.executeQuery()) { require(row.next()); require(row.getInt(1) == 2); require(!row.next()); }
        }
      }
      System.out.println("Verified the exact two QA identities, starter timestamps and exclusive shared room.");
      if (args[0].equals("inspect")) return;
      long now = System.currentTimeMillis();
      for (int i = 0; i < 2; i++) if (present[i]) journal.append(IDS[i], proof(IDS[i]), now, now + 30L * 86400000);
      // The existing live service replays its own durable deletion journal.
      for (int poll = 0; poll < 80; poll++) {
        try (PreparedStatement query = main.prepareStatement("SELECT count(*) FROM guests WHERE id IN (?,?)")) {
          query.setString(1, IDS[0]); query.setString(2, IDS[1]);
          try (ResultSet row = query.executeQuery()) { row.next(); if (row.getInt(1) == 0) { System.out.println("Both interrupted QA profiles removed by durable journal replay."); return; } }
        }
        Thread.sleep(250);
      }
      throw new IllegalStateException("QA deletion intents retained; live replay has not completed within 20 seconds");
    }
  }
}
