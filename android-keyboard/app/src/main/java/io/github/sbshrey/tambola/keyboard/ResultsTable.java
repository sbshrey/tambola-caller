package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** Each player column contains only that player's winnings, followed by their total. */
final class ResultsTable {
    static final class Win {
        final String id, name;
        final int paise;
        final boolean shared;
        Win(PrizeBook.Scheme prize) { id = prize.id; name = prize.name; paise = prize.eachPaise(); shared = prize.winners.size() == 2; }
        String label() { return name + "\n" + PrizeBook.money(paise) + (shared ? " · shared" : ""); }
    }
    static final class Player {
        final String id, name;
        final int sets, totalPaise;
        final List<Win> wins;
        Player(String id, String name, int sets, List<Win> wins) {
            this.id = id; this.name = name; this.sets = sets; this.wins = Collections.unmodifiableList(wins);
            int total = 0; for (Win win : wins) total += win.paise; totalPaise = total;
        }
        String label() { return name + (sets < 0 ? "" : "\n" + sets + (sets == 1 ? " set" : " sets")); }
    }
    final List<Player> players;
    final PrizeSummary summary;
    final TicketPlan plan;
    final int unawarded;
    ResultsTable(PrizeBook book, TicketPlan plan) {
        if (plan != null) plan.validateLive(book);
        this.plan = plan; summary = new PrizeSummary(book);
        List<Player> people = new ArrayList<>();
        for (Map.Entry<String, String> person : book.players.entrySet()) {
            if (plan != null && plan.sets(person.getKey()) == 0) continue;
            List<Win> wins = new ArrayList<>();
            for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled && prize.winners.contains(person.getKey())) wins.add(new Win(prize));
            people.add(new Player(person.getKey(), person.getValue(), plan == null ? -1 : plan.sets(person.getKey()), wins));
        }
        unawarded = TicketPlan.selected(book) - summary.paidPrizes - summary.bogies.size();
        players = Collections.unmodifiableList(people);
    }
    String balance() {
        if (plan == null) return "";
        int allocated = 0; for (int value : plan.allocations.values()) allocated += value;
        return "Collected " + PrizeBook.money(plan.collection() * 100) + " · Unallocated " + PrizeBook.money((plan.collection() - allocated) * 100)
            + " · Unpaid prizes " + PrizeBook.money(allocated * 100 - summary.totalPaise);
    }
}
