package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** Optional prize tracking. Money is integer rupees, split into exact paise. */
final class PrizeBook {
    static final int PRIZE_RUPEES = 10, MAX_PLAYERS = 100, MAX_SCHEMES = 500;
    final Map<String, String> players;
    final List<Scheme> schemes;

    static final class Scheme {
        final String id, name;
        final int rupees, minimumCalls, awardedRupees, callCount;
        final boolean enabled;
        final List<String> winners, bogiePlayers;
        Scheme(String id, String name, int rupees, boolean enabled, int minimumCalls, List<String> winners, int awardedRupees, int callCount) {
            this(id, name, rupees, enabled, minimumCalls, winners, awardedRupees, callCount, Collections.emptyList());
        }
        Scheme(String id, String name, int rupees, boolean enabled, int minimumCalls, List<String> winners, int awardedRupees, int callCount, List<String> bogiePlayers) {
            this.id = id; this.name = clean(name); this.rupees = amount(rupees); this.enabled = enabled;
            this.minimumCalls = minimumCalls; this.winners = Collections.unmodifiableList(new ArrayList<>(winners));
            this.bogiePlayers = Collections.unmodifiableList(new ArrayList<>(bogiePlayers));
            this.awardedRupees = amount(awardedRupees); this.callCount = callCount;
            if (id == null || id.isEmpty() || minimumCalls < 1 || minimumCalls > 90 || callCount < 0 || callCount > 90
                || winners.size() > 2 || new HashSet<>(winners).size() != winners.size()
                || (bogie() && (bogiePlayers.size() < 3 || !winners.isEmpty() || awardedRupees != 0))
                || new HashSet<>(bogiePlayers).size() != bogiePlayers.size()
                || (resolved() && (callCount < minimumCalls || !enabled)) || (!resolved() && callCount != 0)) throw new IllegalArgumentException("Unreadable prize details.");
        }
        boolean bogie() { return !bogiePlayers.isEmpty(); }
        boolean resolved() { return bogie() || !winners.isEmpty(); }
        int eachPaise() { return winners.isEmpty() ? 0 : awardedRupees * 100 / winners.size(); }
        Scheme cleared() { return new Scheme(id, name, rupees, enabled, minimumCalls, Collections.emptyList(), 0, 0); }
    }

    PrizeBook(Map<String, String> players, List<Scheme> schemes) {
        if (players.size() > MAX_PLAYERS || schemes.size() > MAX_SCHEMES) throw new IllegalArgumentException("Use up to 100 players and 500 prize schemes.");
        LinkedHashMap<String, String> checked = new LinkedHashMap<>(); Set<String> names = new HashSet<>(), ids = new HashSet<>();
        players.forEach((id, name) -> { String value = clean(name); if (id == null || id.isEmpty() || !names.add(value.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Player names must be different."); checked.put(id, value); });
        for (Scheme scheme : schemes) { if (!ids.add(scheme.id) || !checked.keySet().containsAll(scheme.winners) || !checked.keySet().containsAll(scheme.bogiePlayers)) throw new IllegalArgumentException("Unreadable prize details."); }
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
    PrizeBook removePlayer(String id) { for (Scheme item : schemes) if (item.winners.contains(id) || item.bogiePlayers.contains(id)) throw new IllegalArgumentException("Clear this player's prize results first."); Map<String, String> next = new LinkedHashMap<>(players); next.remove(id); return new PrizeBook(next, schemes); }
    PrizeBook configure(String id, String name, int rupees, boolean enabled) {
        Scheme old = id == null ? new Scheme(UUID.randomUUID().toString(), name, rupees, enabled, 1, Collections.emptyList(), 0, 0) : scheme(id);
        if (!enabled && old.resolved()) throw new IllegalArgumentException("Clear this prize's result before switching it off.");
        Scheme next = new Scheme(old.id, name, rupees, enabled, old.minimumCalls, old.winners, old.awardedRupees, old.callCount, old.bogiePlayers);
        return replace(next, id == null);
    }
    PrizeBook award(String id, List<String> winners, int rupees, int count) {
        Scheme old = scheme(id); if (!old.enabled) throw new IllegalArgumentException("Turn this prize on first.");
        return replace(new Scheme(id, old.name, old.rupees, old.enabled, old.minimumCalls, winners, rupees, winners.isEmpty() ? 0 : count), false);
    }
    PrizeBook resolve(String id, List<String> claimants, int count) {
        Scheme old = scheme(id);
        if (claimants.isEmpty()) throw new IllegalArgumentException("Choose at least one claimant.");
        int recordedCount = old.resolved() ? old.callCount : count;
        if (claimants.size() < 3) return award(id, claimants, old.winners.isEmpty() ? old.rupees : old.awardedRupees, recordedCount);
        if (!old.enabled) throw new IllegalArgumentException("Turn this prize on first.");
        return replace(new Scheme(id, old.name, old.rupees, old.enabled, old.minimumCalls, Collections.emptyList(), 0, recordedCount, claimants), false);
    }
    PrizeBook addPresets(Collection<String> selected) {
        PrizeBook next = this;
        for (String id : new LinkedHashSet<>(selected)) {
            PrizeCatalog.Preset preset = PrizeCatalog.find(id); Scheme old = PrizeCatalog.existing(next, preset);
            if (old != null) {
                if (!old.enabled) next = next.configure(old.id, old.name, old.rupees, true);
            } else next = next.replace(new Scheme(preset.id, preset.name, 10, true, preset.minimum, Collections.emptyList(), 0, 0), true);
        }
        return next;
    }
    private PrizeBook replace(Scheme item, boolean add) { List<Scheme> next = new ArrayList<>(); for (Scheme old : schemes) next.add(old.id.equals(item.id) ? item : old); if (add) next.add(item); return new PrizeBook(players, next); }
    PrizeBook afterUndo(int count) { List<Scheme> next = new ArrayList<>(); for (Scheme item : schemes) next.add(item.callCount > count ? item.cleared() : item); return new PrizeBook(players, next); }
    PrizeBook fixedPrizes() {
        PrizeBook next = this;
        for (Scheme prize : schemes) if (prize.rupees != PRIZE_RUPEES) next = next.configure(prize.id, prize.name, PRIZE_RUPEES, prize.enabled);
        return next;
    }
    PrizeBook newRound() { return afterUndo(0).fixedPrizes(); }
    String names(List<String> ids) { List<String> names = new ArrayList<>(); for (String id : ids) names.add(players.get(id)); return String.join(" & ", names); }
    String winners(Scheme item) { return item.bogie() ? "Bogie · " + names(item.bogiePlayers) : names(item.winners); }
    String claim(String schemeId, String playerId, int count) {
        Scheme item = scheme(schemeId);
        if (!players.containsKey(playerId)) throw new IllegalArgumentException("Add the player first.");
        if (!item.enabled) throw new IllegalArgumentException("Turn this prize on first.");
        if (item.resolved()) throw new IllegalArgumentException("This prize already has a result.");
        if (count < item.minimumCalls) throw new IllegalArgumentException("Call at least " + item.minimumCalls + " numbers before claiming this prize.");
        return "🙋 " + players.get(playerId) + " claims " + item.name + "\nPlease check my ticket.";
    }
    String announcement(Scheme item) {
        if (item.bogie()) return "🚫 " + item.name + ": Bogie\n" + item.bogiePlayers.size() + " claimants: " + names(item.bogiePlayers) + "\nNo payout";
        return "🏆 " + item.name + ": " + winners(item) + "\n" + money(item.eachPaise()) + (item.winners.size() == 2 ? " each" : " prize");
    }
    boolean hasWinners() { for (Scheme item : schemes) if (item.resolved()) return true; return false; }
    String results(int count) {
        StringBuilder text = new StringBuilder("🏆 Tambola results\n" + count + " numbers called\n"); Map<String, Integer> totals = new LinkedHashMap<>();
        for (Scheme item : schemes) if (item.enabled) {
            text.append('\n').append(item.resolved() ? announcement(item) : item.name + ": not awarded").append('\n');
            for (String id : item.winners) totals.put(id, totals.getOrDefault(id, 0) + item.eachPaise());
        }
        if (!totals.isEmpty()) { text.append("\nTotal prizes\n"); totals.forEach((id, total) -> text.append(players.get(id)).append(": ").append(money(total)).append('\n')); }
        return text.toString().trim();
    }
}
