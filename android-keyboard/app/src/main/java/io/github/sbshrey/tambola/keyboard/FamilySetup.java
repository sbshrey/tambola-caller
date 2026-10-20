package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** Optional, repeatable setup for the host's regular group. Never replaces saved details. */
final class FamilySetup {
    static final List<String> PLAYERS = Collections.unmodifiableList(Arrays.asList(
        "Varsha", "Rekha", "Kamlesh Bhabhi", "Laxmi Bhabhi", "Leela jiji", "Nirmala",
        "Rekha totla", "Sandhya", "Sangita jiji", "Saroj", "Suman"));

    private static String id(String name) { return "family-" + name.toLowerCase(Locale.ROOT).replace(' ', '-'); }
    private static boolean contains(PrizeBook book, String name) {
        if (book.players.containsKey(id(name))) return true; // Keep a host's renamed player.
        for (String saved : book.players.values()) if (saved.equalsIgnoreCase(name)) return true;
        return false;
    }
    static boolean hasPlayers(PrizeBook book) {
        for (String name : PLAYERS) if (!contains(book, name)) return false;
        return true;
    }
    static String hostId(PrizeBook book) {
        if (book.players.containsKey("family-varsha")) return "family-varsha";
        for (Map.Entry<String, String> player : book.players.entrySet())
            if (player.getValue().equalsIgnoreCase("Varsha")) return player.getKey();
        return null;
    }
    static PrizeBook addTo(PrizeBook book) {
        Map<String, String> players = new LinkedHashMap<>(book.players);
        for (String name : PLAYERS) if (!contains(book, name)) players.put(id(name), name);
        // Immutable construction means either the whole setup succeeds or nothing changes.
        return new PrizeBook(players, book.schemes).addPresets(PrizeCatalog.allIds());
    }
}
