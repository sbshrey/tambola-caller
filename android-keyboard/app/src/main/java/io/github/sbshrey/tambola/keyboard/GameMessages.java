package io.github.sbshrey.tambola.keyboard;

import java.util.Map;

/** Read-only announcements made from the confirmed ticket and prize setup. */
final class GameMessages {
    enum Kind {
        PLAYERS("Players & tickets", "players & tickets"),
        SCHEMES("Selected schemes", "selected schemes"),
        START("Start message", "start message");
        final String label, action;
        Kind(String label, String action) { this.label = label; this.action = action; }
    }
    static String text(Kind kind, PrizeBook book, TicketPlan plan, Game game) {
        if (plan == null || !plan.approved)
            throw new IllegalArgumentException("Confirm tickets & schemes before sharing game messages.");
        if (game.count() == 0 && book.schemes.stream().anyMatch(prize -> prize.enabled && prize.rupees != PrizeBook.PRIZE_RUPEES))
            throw new IllegalArgumentException("Review and confirm the ₹10 schemes before sharing game messages.");
        plan.validateLive(book);
        switch (kind) {
            case PLAYERS: return players(book, plan);
            case SCHEMES: return schemes(book);
            case START:
                if (game.count() > 0 || book.hasWinners()) throw new IllegalArgumentException("This game has already started. You can still share players and schemes.");
                return "🎉 Let's start Tambola!\n\n" + summary(plan)
                    + "\nPrize pool: " + PrizeBook.money(plan.collection() * 100)
                    + " · " + TicketPlan.selected(book) + " schemes at ₹10 each"
                    + "\n\nPlease keep your tickets ready. Claim in the group as soon as you complete a selected scheme."
                    + " The host will check all claims and announce the result."
                    + "\n\nEveryone playing can win any selected scheme.\n" + rules(book)
                    + "\n\nGood luck, everyone! 🍀";
            default: throw new IllegalArgumentException("Choose a game message.");
        }
    }
    private static String summary(TicketPlan plan) {
        return plan.players() + (plan.players() == 1 ? " player · " : " players · ")
            + plan.totalSets() + (plan.totalSets() == 1 ? " set · " : " sets · ") + plan.totalSets() * TicketPlan.TICKETS + " tickets";
    }
    private static String players(PrizeBook book, TicketPlan plan) {
        StringBuilder text = new StringBuilder("🎟️ Tambola players & tickets\n1 set = 6 tickets = ₹50\n");
        int number = 0;
        for (Map.Entry<String, String> player : book.players.entrySet()) {
            int sets = plan.sets(player.getKey()); if (sets == 0) continue;
            text.append('\n').append(++number).append(". ").append(player.getValue()).append(" — ")
                .append(sets).append(sets == 1 ? " set (" : " sets (").append(sets * TicketPlan.TICKETS)
                .append(" tickets) · ").append(PrizeBook.money(sets * TicketPlan.PRICE * 100));
        }
        return text.append("\n\nTotal: ").append(summary(plan)).append("\nCollected: ")
            .append(PrizeBook.money(plan.collection() * 100)).toString();
    }
    private static String schemes(PrizeBook book) {
        StringBuilder text = new StringBuilder("🎯 Tambola selected schemes\n")
            .append(TicketPlan.selected(book)).append(" prizes · ").append(PrizeBook.money(TicketPlan.budget(book) * 100)).append(" total\n");
        int number = 0;
        for (PrizeBook.Scheme prize : book.schemes) if (prize.enabled)
            text.append('\n').append(++number).append(". ").append(prize.name).append(" — ").append(PrizeBook.money(prize.rupees * 100));
        return text.append("\n\nEveryone playing can win any selected scheme.\n").append(rules(book)).toString();
    }
    private static String rules(PrizeBook book) {
        boolean ten = book.schemes.stream().filter(prize -> prize.enabled).allMatch(prize -> prize.rupees == PrizeBook.PRIZE_RUPEES);
        return (ten ? "One winner: ₹10. Two winners: ₹5 each." : "One winner receives the full prize. Two winners share it equally.")
            + "\nThree or more valid claimants: Bogie — no payout.";
    }
}
