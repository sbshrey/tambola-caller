package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameMessagesTest {
    private PrizeBook book() {
        Map<String, String> names = new LinkedHashMap<>(); names.put("v", "Varsha"); names.put("r", "Rekha"); names.put("s", "Suman");
        PrizeBook book = new PrizeBook(names, PrizeBook.defaults().schemes).configure("prize-4", "Full house", 10, false);
        while (TicketPlan.selected(book) < 15) book = book.configure(null, "Extra " + book.schemes.size(), 10, true);
        return book;
    }
    private TicketPlan plan(PrizeBook book) { return TicketPlan.forPlayers(book).with("v", 2).with("s", 0).approve(book); }
    @Test public void playersDistinguishSetsFromTicketsAndExcludeNonParticipants() {
        PrizeBook book = book(); String text = GameMessages.text(GameMessages.Kind.PLAYERS, book, plan(book), Game.empty());
        assertTrue(text.contains("1 set = 6 tickets = ₹50"));
        assertTrue(text.contains("1. Varsha — 2 sets (12 tickets) · ₹100"));
        assertTrue(text.contains("2. Rekha — 1 set (6 tickets) · ₹50"));
        assertTrue(text.contains("Total: 2 players · 3 sets · 18 tickets\nCollected: ₹150")); assertFalse(text.contains("Suman"));
    }
    @Test public void schemesIncludeEverySelectedPrizeAndExcludeDisabledOnes() {
        PrizeBook book = book(); String text = GameMessages.text(GameMessages.Kind.SCHEMES, book, plan(book), Game.empty());
        assertTrue(text.contains("15 prizes · ₹150 total")); assertFalse(text.contains("Full house")); int number = 0;
        for (PrizeBook.Scheme scheme : book.schemes) if (scheme.enabled) assertTrue(text.contains(++number + ". " + scheme.name + " — ₹10"));
        assertTrue(text.contains("Two winners: ₹5 each.")); assertTrue(text.contains("Three or more valid claimants: Bogie — no payout."));
    }
    @Test public void startUsesConfirmedTotalsWithoutDrawingOrAwarding() {
        PrizeBook book = book(); TicketPlan plan = plan(book); Game game = Game.empty();
        String text = GameMessages.text(GameMessages.Kind.START, book, plan, game);
        assertTrue(text.startsWith("🎉 Let's start Tambola!")); assertTrue(text.contains("Prize pool: ₹150 · 15 schemes at ₹10 each"));
        assertTrue(text.contains("host will check all claims")); assertEquals(0, game.count()); assertFalse(book.hasWinners()); assertTrue(plan.approved);
    }
    @Test public void unconfirmedMissingAndChangedPlansCannotProduceAnnouncements() {
        PrizeBook book = book(); TicketPlan plan = plan(book);
        for (GameMessages.Kind kind : GameMessages.Kind.values()) {
            assertThrows(IllegalArgumentException.class, () -> GameMessages.text(kind, book, null, Game.empty()));
            assertThrows(IllegalArgumentException.class, () -> GameMessages.text(kind, book, plan.unapproved(), Game.empty()));
            assertThrows(IllegalArgumentException.class, () -> GameMessages.text(kind, book.configure("prize-0", "Early 5", 10, false), plan, Game.empty()));
            assertThrows(IllegalArgumentException.class, () -> GameMessages.text(kind, book.player(null, "New person"), plan, Game.empty()));
        }
    }
    @Test public void duringPlayListsRemainAvailableButStartRequiresANewConfirmedRound() {
        PrizeBook book = book(); TicketPlan plan = plan(book); Game game = Game.decode("1,2,3,4,5");
        assertThrows(IllegalArgumentException.class, () -> GameMessages.text(GameMessages.Kind.START, book, plan, game));
        assertTrue(GameMessages.text(GameMessages.Kind.PLAYERS, book, plan, game).contains("Varsha"));
        PrizeBook awarded = book.resolve("prize-0", Collections.singletonList("v"), 5);
        assertThrows(IllegalArgumentException.class, () -> GameMessages.text(GameMessages.Kind.START, awarded, plan, Game.empty()));
        PrizeBook next = awarded.newRound(); TicketPlan review = plan.review(next);
        assertThrows(IllegalArgumentException.class, () -> GameMessages.text(GameMessages.Kind.START, next, review, Game.empty()));
        assertTrue(GameMessages.text(GameMessages.Kind.START, next, review.approve(next), Game.empty()).contains("Let's start"));
    }
    @Test public void hindiNamesAndAllFiveHundredSchemesAreIncludedWithoutTruncation() {
        Map<String, String> names = new LinkedHashMap<>(); Map<String, Integer> sets = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) { names.put("p" + i, "खिलाड़ी " + i); sets.put("p" + i, 1); }
        PrizeBook book = new PrizeBook(names, PrizeBook.defaults().schemes);
        while (book.schemes.size() < 500) book = book.configure(null, "उल्टी सीधी " + book.schemes.size(), 10, true);
        TicketPlan plan = new TicketPlan(sets, Collections.emptyMap(), false).approve(book);
        String players = GameMessages.text(GameMessages.Kind.PLAYERS, book, plan, Game.empty());
        assertTrue(players.contains("100. खिलाड़ी 99 — 1 set (6 tickets) · ₹50")); assertTrue(players.contains("Collected: ₹5000"));
        String schemes = GameMessages.text(GameMessages.Kind.SCHEMES, book, plan, Game.empty());
        assertTrue(schemes.contains("500. उल्टी सीधी 499 — ₹10")); assertTrue(schemes.contains("500 prizes · ₹5000 total"));
    }
    @Test public void approvedOlderGamesDescribeActualAmountsButCannotStartWithOldPrices() {
        PrizeBook book = new PrizeBook(Collections.singletonMap("v", "Varsha"), PrizeBook.defaults().schemes)
            .configure("prize-0", "Early 5", 20, true).configure("prize-1", "Top line", 0, true);
        Map<String, Integer> amounts = new LinkedHashMap<>(); for (PrizeBook.Scheme scheme : book.schemes) amounts.put(scheme.id, scheme.rupees);
        TicketPlan plan = new TicketPlan(Collections.singletonMap("v", 1), amounts, true);
        String text = GameMessages.text(GameMessages.Kind.SCHEMES, book, plan, Game.decode("1"));
        assertTrue(text.contains("Early 5 — ₹20")); assertTrue(text.contains("Two winners share it equally.")); assertFalse(text.contains("₹5 each"));
        for (GameMessages.Kind kind : GameMessages.Kind.values()) assertThrows(IllegalArgumentException.class, () -> GameMessages.text(kind, book, plan, Game.empty()));
    }
}
