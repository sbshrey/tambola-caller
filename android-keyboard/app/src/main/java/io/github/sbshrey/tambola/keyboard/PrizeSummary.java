package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** A snapshot of actual per-player winnings. Bogies never contribute to money totals. */
final class PrizeSummary {
    static final class Player {
        final String name;
        final List<String> prizes;
        final int totalPaise;
        Player(String name, List<String> prizes, int total) {
            this.name = name; this.prizes = Collections.unmodifiableList(prizes); totalPaise = total;
        }
    }
    static final class Bogie {
        final String prize;
        final List<String> names;
        Bogie(String prize, List<String> names) { this.prize = prize; this.names = Collections.unmodifiableList(names); }
    }
    final List<Player> players;
    final List<Bogie> bogies;
    final int totalPaise, paidPrizes;
    PrizeSummary(PrizeBook book) {
        List<Player> people = new ArrayList<>(); List<Bogie> noPayout = new ArrayList<>();
        int total = 0, paid = 0;
        for (Map.Entry<String, String> person : book.players.entrySet()) {
            List<String> wins = new ArrayList<>(); int playerTotal = 0;
            for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled && prize.winners.contains(person.getKey())) {
                wins.add(prize.name + " · " + PrizeBook.money(prize.eachPaise()) + (prize.winners.size() == 2 ? " (shared)" : ""));
                playerTotal += prize.eachPaise();
            }
            people.add(new Player(person.getValue(), wins, playerTotal)); total += playerTotal;
        }
        for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled) {
            if (!prize.winners.isEmpty()) paid++;
            if (prize.bogie()) {
                List<String> names = new ArrayList<>(); for (String id : prize.bogiePlayers) names.add(book.players.get(id));
                noPayout.add(new Bogie(prize.name, names));
            }
        }
        players = Collections.unmodifiableList(people); bogies = Collections.unmodifiableList(noPayout);
        totalPaise = total; paidPrizes = paid;
    }
}
