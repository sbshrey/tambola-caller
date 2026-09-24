package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** Optional prize tracking. Money is integer rupees, split into exact paise. */
final class PrizeBook {
    static final int MAX_PLAYERS = 100, MAX_SCHEMES = 20;
    final Map<String, String> players;
    final List<Scheme> schemes;

    static final class Scheme {
        final String id, name;
        final int rupees, minimumCalls, awardedRupees, callCount;
        final boolean enabled;
        final List<String> winners;
        Scheme(String id, String name, int rupees, boolean enabled, int minimumCalls, List<String> winners, int awardedRupees, int callCount) {
            this.id = id; this.name = clean(name); this.rupees = amount(rupees); this.enabled = enabled;
            this.minimumCalls = minimumCalls; this.winners = Collections.unmodifiableList(new ArrayList<>(winners));
            this.awardedRupees = amount(awardedRupees); this.callCount = callCount;
            if (id == null || id.isEmpty() || minimumCalls < 1 || minimumCalls > 90 || callCount < 0 || callCount > 90
                || winners.size() > 2 || new HashSet<>(winners).size() != winners.size()
                || (!winners.isEmpty() && (callCount < minimumCalls || !enabled)) || (winners.isEmpty() && callCount != 0)) throw new IllegalArgumentException("Unreadable prize details.");
        }
        int eachPaise() { return winners.isEmpty() ? 0 : awardedRupees * 100 / winners.size(); }
        Scheme cleared() { return new Scheme(id, name, rupees, enabled, minimumCalls, Collections.emptyList(), 0, 0); }
    }

    PrizeBook(Map<String, String> players, List<Scheme> schemes) {
        if (players.size() > MAX_PLAYERS || schemes.size() > MAX_SCHEMES) throw new IllegalArgumentException("Too many players or prizes.");
        LinkedHashMap<String, String> checked = new LinkedHashMap<>(); Set<String> names = new HashSet<>(), ids = new HashSet<>();
        players.forEach((id, name) -> { String value = clean(name); if (id == null || id.isEmpty() || !names.add(value.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Player names must be different."); checked.put(id, value); });
        for (Scheme scheme : schemes) { if (!ids.add(scheme.id) || !checked.keySet().containsAll(scheme.winners)) throw new IllegalArgumentException("Unreadable prize details."); }
        this.players = Collections.unmodifiableMap(checked); this.schemes = Collections.unmodifiableList(new ArrayList<>(schemes));
    }
    static PrizeBook defaults() {
        List<Scheme> values = new ArrayList<>(); String[] names = {"Early 5", "Top line", "Middle line", "Bottom line", "Full house"};
        for (int i = 0; i < names.length; i++) values.add(new Scheme("prize-" + i, names[i], 10, true, i == 4 ? 15 : 5, Collections.emptyList(), 0, 0));
        return new PrizeBook(Collections.emptyMap(), values);
    }
    static String clean(String value) { if (value == null) throw new IllegalArgumentException("Enter a name."); value = value.trim().replaceAll("\\s+", " "); if (value.isEmpty() || value.length() > 60) throw new IllegalArgumentException("Use a name of 1–60 characters."); return value; }
    static int amount(int value) { if (value < 0 || value > 100000) throw new IllegalArgumentException("Enter a whole-rupee prize from 0 to 100000."); return value; }
    static String money(int paise) { return "₹" + (paise / 100) + (paise % 100 == 0 ? "" : String.format(Locale.ROOT, ".%02d", paise % 100)); }
    Scheme scheme(String id) { for (Scheme item : schemes) if (item.id.equals(id)) return item; throw new IllegalArgumentException("Prize not found."); }
    PrizeBook player(String id, String name) { Map<String, String> next = new LinkedHashMap<>(players); if (id != null && !next.containsKey(id)) throw new IllegalArgumentException("Player not found."); next.put(id == null ? UUID.randomUUID().toString() : id, clean(name)); return new PrizeBook(next, schemes); }
    PrizeBook removePlayer(String id) { for (Scheme item : schemes) if (item.winners.contains(id)) throw new IllegalArgumentException("Clear this player's wins first."); Map<String, String> next = new LinkedHashMap<>(players); next.remove(id); return new PrizeBook(next, schemes); }
    PrizeBook configure(String id, String name, int rupees, boolean enabled) {
        Scheme old = id == null ? new Scheme(UUID.randomUUID().toString(), name, rupees, enabled, 1, Collections.emptyList(), 0, 0) : scheme(id);
        if (!enabled && !old.winners.isEmpty()) throw new IllegalArgumentException("Clear this prize's winners before switching it off.");
        Scheme next = new Scheme(old.id, name, rupees, enabled, old.minimumCalls, old.winners, old.awardedRupees, old.callCount);
        return replace(next, id == null);
    }
    PrizeBook award(String id, List<String> winners, int rupees, int count) {
        Scheme old = scheme(id); if (!old.enabled) throw new IllegalArgumentException("Turn this prize on first.");
        return replace(new Scheme(id, old.name, old.rupees, old.enabled, old.minimumCalls, winners, rupees, winners.isEmpty() ? 0 : count), false);
    }
    private PrizeBook replace(Scheme item, boolean add) { List<Scheme> next = new ArrayList<>(); for (Scheme old : schemes) next.add(old.id.equals(item.id) ? item : old); if (add) next.add(item); return new PrizeBook(players, next); }
    PrizeBook afterUndo(int count) { List<Scheme> next = new ArrayList<>(); for (Scheme item : schemes) next.add(item.callCount > count ? item.cleared() : item); return new PrizeBook(players, next); }
    PrizeBook newRound() { return afterUndo(0); }
    String winners(Scheme item) { List<String> names = new ArrayList<>(); for (String id : item.winners) names.add(players.get(id)); return String.join(" & ", names); }
    String announcement(Scheme item) { return "🏆 " + item.name + ": " + winners(item) + "\n" + money(item.eachPaise()) + (item.winners.size() == 2 ? " each" : " prize"); }
    boolean hasWinners() { for (Scheme item : schemes) if (!item.winners.isEmpty()) return true; return false; }
    String results(int count) {
        StringBuilder text = new StringBuilder("🏆 Tambola results\n" + count + " numbers called\n"); Map<String, Integer> totals = new LinkedHashMap<>();
        for (Scheme item : schemes) if (item.enabled) {
            text.append('\n').append(item.winners.isEmpty() ? item.name + ": not awarded" : announcement(item)).append('\n');
            for (String id : item.winners) totals.put(id, totals.getOrDefault(id, 0) + item.eachPaise());
        }
        if (!totals.isEmpty()) { text.append("\nTotal prizes\n"); totals.forEach((id, total) -> text.append(players.get(id)).append(": ").append(money(total)).append('\n')); }
        return text.toString().trim();
    }
}
