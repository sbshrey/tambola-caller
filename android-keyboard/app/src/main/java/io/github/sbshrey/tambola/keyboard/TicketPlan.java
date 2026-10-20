package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** One purchased set is six paper tickets for ₹50. A player is counted once per claim. */
final class TicketPlan {
    static final int PRICE = 50, TICKETS = 6, MAX_SETS = 20;
    final Map<String, Integer> sets, allocations;
    final boolean approved;
    TicketPlan(Map<String, Integer> sets, Map<String, Integer> allocations, boolean approved) {
        if (sets.size() > PrizeBook.MAX_PLAYERS) throw new IllegalArgumentException("Too many players.");
        sets.forEach((id, count) -> { if (id == null || id.isEmpty() || count == null || count < 0 || count > MAX_SETS) throw new IllegalArgumentException("Choose 0–20 sets per player."); });
        allocations.forEach((id, amount) -> { if (id == null || id.isEmpty()) throw new IllegalArgumentException("Unreadable ticket plan."); PrizeBook.amount(amount); });
        if (allocations.size() > PrizeBook.MAX_SCHEMES || (!approved && !allocations.isEmpty())) throw new IllegalArgumentException("Unreadable ticket plan.");
        this.sets = Collections.unmodifiableMap(new LinkedHashMap<>(sets)); this.allocations = Collections.unmodifiableMap(new LinkedHashMap<>(allocations)); this.approved = approved;
    }
    static TicketPlan forPlayers(PrizeBook book) { Map<String, Integer> sets = new LinkedHashMap<>(); for (String id : book.players.keySet()) sets.put(id, 1); return new TicketPlan(sets, Collections.emptyMap(), false); }
    int sets(String id) { return sets.getOrDefault(id, 0); }
    int totalSets() { int total = 0; for (int count : sets.values()) total += count; return total; }
    int collection() { return totalSets() * PRICE; }
    int players() { int count = 0; for (int value : sets.values()) if (value > 0) count++; return count; }
    TicketPlan with(String id, int count) { if (!sets.containsKey(id)) throw new IllegalArgumentException("Player not found."); Map<String, Integer> next = new LinkedHashMap<>(sets); next.put(id, count); return new TicketPlan(next, Collections.emptyMap(), false); }
    TicketPlan review(PrizeBook book) { Map<String, Integer> next = new LinkedHashMap<>(); for (String id : book.players.keySet()) next.put(id, sets.getOrDefault(id, 1)); return new TicketPlan(next, Collections.emptyMap(), false); }
    TicketPlan unapproved() { return new TicketPlan(sets, Collections.emptyMap(), false); }
    static int budget(PrizeBook book) { int total = 0; for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled) total += prize.rupees; return total; }
    static int selected(PrizeBook book) { int count = 0; for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled) count++; return count; }
    String problem(PrizeBook book) {
        if (!sets.keySet().equals(book.players.keySet())) return "Player list changed. Review ticket sales again.";
        if (totalSets() == 0) return "Choose at least one purchased set.";
        if (selected(book) == 0) return "Select at least one prize scheme.";
        int difference = collection() - budget(book);
        if (difference > 0) return "Allocate the remaining " + PrizeBook.money(difference * 100) + " to prizes.";
        if (difference < 0) return "Reduce prizes by " + PrizeBook.money(-difference * 100) + " to match ticket sales.";
        return null;
    }
    TicketPlan approve(PrizeBook book) {
        String error = problem(book); if (error != null) throw new IllegalArgumentException(error);
        Map<String, Integer> amounts = new LinkedHashMap<>(); for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled) amounts.put(prize.id, prize.rupees);
        return new TicketPlan(sets, amounts, true);
    }
    PrizeBook balance(PrizeBook book) {
        int count = selected(book); if (totalSets() == 0 || count == 0) throw new IllegalArgumentException("Choose purchased sets and at least one scheme first.");
        if (collection() < count) throw new IllegalArgumentException("Select fewer schemes so every prize can receive at least ₹1.");
        int base = collection() / count, remainder = collection() % count; PrizeBook next = book;
        for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled) next = next.configure(prize.id, prize.name, base + (remainder-- > 0 ? 1 : 0), true);
        return next;
    }
    void validateLive(PrizeBook book) {
        if (!approved) throw new IllegalArgumentException("Review tickets and schemes before calling the first number.");
        String error = problem(book); if (error != null) throw new IllegalArgumentException(error);
        Map<String, Integer> amounts = new LinkedHashMap<>();
        for (PrizeBook.Scheme prize : book.schemes) {
            if (prize.enabled) amounts.put(prize.id, prize.rupees);
            for (String id : prize.bogie() ? prize.bogiePlayers : prize.winners) if (sets(id) == 0) throw new IllegalArgumentException("Only players with purchased sets can claim this game.");
            if (!prize.winners.isEmpty() && prize.awardedRupees != prize.rupees) throw new IllegalArgumentException("Use this game's planned prize amount. Change the budget before a new game.");
        }
        if (!allocations.equals(amounts)) throw new IllegalArgumentException("This game's schemes and amounts are fixed. Review them before a new game.");
    }
}
