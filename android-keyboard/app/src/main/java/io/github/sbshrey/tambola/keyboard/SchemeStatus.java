package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** A snapshot of selected schemes and saved host decisions, independent of chat delivery. */
final class SchemeStatus {
    enum State { REMAINING, WON, BOGIE }
    static final class Entry {
        final String id, name, detail;
        final State state;
        final int paidPaise;
        Entry(PrizeBook book, PrizeBook.Scheme prize) {
            id = prize.id; name = prize.name;
            state = prize.bogie() ? State.BOGIE : prize.winners.isEmpty() ? State.REMAINING : State.WON;
            List<String> lines = new ArrayList<>(); int paid = 0;
            if (state == State.REMAINING) lines.add("Still open · " + PrizeBook.money(prize.rupees * 100));
            else if (state == State.BOGIE) {
                lines.add("Bogie · still open · " + PrizeBook.money(prize.rupees * 100) + " available"); List<String> names = new ArrayList<>();
                for (String id : prize.bogiePlayers) names.add(book.players.get(id)); lines.add("No payout. Previous claimants: " + String.join(", ", names));
            } else {
                if (prize.winners.size() == 2) lines.add("Shared prize");
                for (String id : prize.winners) { lines.add(book.players.get(id) + " · " + PrizeBook.money(prize.eachPaise())); paid += prize.eachPaise(); }
            }
            detail = String.join("\n", lines); paidPaise = paid;
        }
    }
    final List<Entry> remaining, won, bogies;
    final int selected, paidPaise;
    SchemeStatus(PrizeBook book, TicketPlan plan) {
        if (plan != null) plan.validateLive(book);
        List<Entry> open = new ArrayList<>(), paid = new ArrayList<>(), bogie = new ArrayList<>(); int total = 0;
        for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled) {
            Entry entry = new Entry(book, prize);
            (entry.state == State.WON ? paid : open).add(entry);
            if (entry.state == State.BOGIE) bogie.add(entry); total += entry.paidPaise;
        }
        selected = open.size() + paid.size(); if (selected == 0) throw new IllegalArgumentException("Select at least one scheme before sharing.");
        remaining = Collections.unmodifiableList(open); won = Collections.unmodifiableList(paid); bogies = Collections.unmodifiableList(bogie); paidPaise = total;
    }
}
