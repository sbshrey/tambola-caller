package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** Schemes are rows and players are columns, shared by the screen and exported pictures. */
final class ResultsTable {
    static final class Player {
        final String id, name;
        final int sets, totalPaise;
        Player(String id, String name, int sets, int totalPaise) { this.id = id; this.name = name; this.sets = sets; this.totalPaise = totalPaise; }
        String label() { return name + (sets < 0 ? "" : "\n" + sets + (sets == 1 ? " set" : " sets")); }
    }
    static final class Row {
        final String id, name, status;
        final boolean bogie;
        final List<Integer> shares;
        final int totalPaise;
        Row(PrizeBook.Scheme prize, Collection<String> players) {
            id = prize.id; name = prize.name; bogie = prize.bogie();
            status = bogie ? "Bogie · no payout" : prize.winners.isEmpty() ? "Not awarded" : prize.winners.size() == 2 ? "Shared" : "Winner";
            List<Integer> amounts = new ArrayList<>(); int total = 0;
            for (String player : players) { int share = prize.winners.contains(player) ? prize.eachPaise() : 0; amounts.add(share); total += share; }
            shares = Collections.unmodifiableList(amounts); totalPaise = total;
        }
        String label() { return name + "\n" + status; }
        String share(int column) { return shares.get(column) == 0 ? "—" : PrizeBook.money(shares.get(column)); }
    }
    final List<Player> players;
    final List<Row> rows;
    final PrizeSummary summary;
    final TicketPlan plan;
    final int unawarded;
    ResultsTable(PrizeBook book, TicketPlan plan) {
        if (plan != null) plan.validateLive(book);
        this.plan = plan; summary = new PrizeSummary(book);
        List<Player> people = new ArrayList<>(); int index = 0;
        for (Map.Entry<String, String> person : book.players.entrySet()) people.add(new Player(person.getKey(), person.getValue(),
            plan == null ? -1 : plan.sets(person.getKey()), summary.players.get(index++).totalPaise));
        List<Row> prizes = new ArrayList<>();
        for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled && prize.resolved()) prizes.add(new Row(prize, book.players.keySet()));
        unawarded = TicketPlan.selected(book) - prizes.size();
        players = Collections.unmodifiableList(people); rows = Collections.unmodifiableList(prizes);
    }
    String balance() {
        if (plan == null) return "";
        int allocated = 0; for (int value : plan.allocations.values()) allocated += value;
        return "Collected " + PrizeBook.money(plan.collection() * 100) + " · Unallocated " + PrizeBook.money((plan.collection() - allocated) * 100)
            + " · Unpaid prizes " + PrizeBook.money(allocated * 100 - summary.totalPaise);
    }
}
