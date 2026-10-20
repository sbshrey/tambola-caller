package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TicketPlanTest {
    private PrizeBook book() { return FamilySetup.addTo(PrizeBook.defaults()); }
    private PrizeBook funded(PrizeBook book, TicketPlan plan) {
        while (TicketPlan.selected(book) < plan.totalSets() * 5) book = book.configure(null, "Extra " + book.schemes.size(), 10, true);
        return book;
    }
    @Test public void mixedSetsChargeFiftyPerSixTicketsAndExcludeNonParticipants() {
        TicketPlan plan = TicketPlan.forPlayers(book()).with("family-varsha", 2).with("family-suman", 0);
        assertEquals(11, plan.totalSets()); assertEquals(66, plan.totalSets() * TicketPlan.TICKETS); assertEquals(550, plan.collection()); assertEquals(10, plan.players());
        assertEquals(2, plan.sets("family-varsha")); assertEquals(0, plan.sets("family-suman"));
        assertThrows(IllegalArgumentException.class, () -> plan.with("family-varsha", -1)); assertThrows(IllegalArgumentException.class, () -> plan.with("family-varsha", 21));
    }
    @Test public void approvalRequiresFiveTenRupeePrizesPerSetAndNeverChangesAmounts() {
        PrizeBook book = book(); TicketPlan plan = TicketPlan.forPlayers(book());
        assertTrue(plan.problem(book).contains("9 more ₹10 prizes")); assertThrows(IllegalArgumentException.class, () -> plan.approve(book));
        PrizeBook complete = funded(book, plan); assertEquals(55, TicketPlan.selected(complete)); assertEquals(550, TicketPlan.budget(complete));
        TicketPlan approved = plan.approve(complete); assertTrue(approved.approved); approved.validateLive(complete);
        for (PrizeBook.Scheme prize : complete.schemes) assertEquals(10, prize.rupees);
        PrizeBook tooMuch = complete.configure(null, "Extra", 10, true); assertTrue(plan.problem(tooMuch).contains("Remove 1"));
        PrizeBook changed = complete.configure("prize-0", "Early 5", 15, true).configure("catalog-king", "King", 5, true);
        assertEquals(550, TicketPlan.budget(changed)); assertThrows(IllegalArgumentException.class, () -> plan.approve(changed));
    }
    @Test public void aLivePlanLocksAmountsButAllowsWinsSharesBogiesAndCorrections() {
        TicketPlan draft = TicketPlan.forPlayers(book()).with("family-suman", 0); PrizeBook book = funded(book(), draft); TicketPlan plan = draft.approve(book);
        PrizeBook winners = book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 5); plan.validateLive(winners); assertEquals(500, winners.scheme("prize-0").eachPaise());
        plan.validateLive(book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha", "family-nirmala"), 5));
        assertThrows(IllegalArgumentException.class, () -> plan.validateLive(book.resolve("prize-0", Collections.singletonList("family-suman"), 5)));
        assertThrows(IllegalArgumentException.class, () -> plan.validateLive(book.award("prize-0", Collections.singletonList("family-varsha"), 999, 5)));
        PrizeBook changed = book.configure("prize-0", "Early 5", 15, true).configure("catalog-king", "King", 5, true);
        assertThrows(IllegalArgumentException.class, () -> plan.validateLive(changed)); plan.validateLive(winners.player("family-varsha", "Varsha ji"));
    }
    @Test public void nextGameKeepsPurchasesButResetsOldAmountsToTenAndRequiresApproval() {
        TicketPlan plan = TicketPlan.forPlayers(book()).with("family-varsha", 2); PrizeBook complete = funded(book(), plan);
        TicketPlan next = plan.approve(complete).review(complete.newRound()); assertFalse(next.approved); assertEquals(2, next.sets("family-varsha"));
        assertThrows(IllegalArgumentException.class, () -> next.validateLive(complete));
        TicketPlan added = next.review(complete.player(null, "New player")); assertEquals(13, added.totalSets());
        PrizeBook old = complete.configure("prize-0", "Early 5", 100, true).resolve("prize-0", Collections.singletonList("family-varsha"), 5);
        assertEquals(10000, old.scheme("prize-0").eachPaise()); assertEquals(10, old.newRound().scheme("prize-0").rupees); assertFalse(old.newRound().hasWinners());
    }
    @Test public void noEntriesNoSelectedSchemesAndTooManySetsCannotStart() {
        PrizeBook book = book(); TicketPlan zero = TicketPlan.forPlayers(book()); for (String id : book.players.keySet()) zero = zero.with(id, 0);
        assertTrue(zero.problem(book).contains("at least one purchased"));
        PrizeBook none = book; for (PrizeBook.Scheme prize : book.schemes) none = none.configure(prize.id, prize.name, prize.rupees, false);
        assertTrue(TicketPlan.forPlayers(book).problem(none).contains("at least one prize"));
        TicketPlan excessive = TicketPlan.forPlayers(book); for (String id : book.players.keySet()) excessive = excessive.with(id, 20);
        assertTrue(excessive.problem(book).contains("up to 100"));
    }
    @Test public void twoSetsForEveryFamilyPlayerCanAllocateAllOneHundredTenSchemes() {
        PrizeBook book = book(); TicketPlan plan = TicketPlan.forPlayers(book); for (String id : book.players.keySet()) plan = plan.with(id, 2);
        PrizeBook complete = funded(book, plan); assertEquals(110, TicketPlan.selected(complete)); assertEquals(1100, TicketPlan.budget(complete)); plan.approve(complete).validateLive(complete);
    }
    @Test public void alreadyApprovedOlderGamesKeepTheirAmountsUntilNewRound() {
        PrizeBook old = new PrizeBook(Collections.singletonMap("a", "A"), PrizeBook.defaults().schemes); old = old.configure("prize-0", "Early 5", 20, true).configure("prize-1", "Top line", 0, true);
        Map<String, Integer> amounts = new LinkedHashMap<>(); for (PrizeBook.Scheme prize : old.schemes) amounts.put(prize.id, prize.rupees);
        TicketPlan saved = new TicketPlan(Collections.singletonMap("a", 1), amounts, true);
        saved.validateLive(old.resolve("prize-0", Collections.singletonList("a"), 5));
        saved.review(old.newRound()).approve(old.newRound()).validateLive(old.newRound());
    }
    @Test public void olderApprovedGameAboveTheNewSetLimitCanFinishBeforeReview() {
        Map<String, String> names = new LinkedHashMap<>(); Map<String, Integer> sets = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) { names.put("p" + i, "Player " + i); sets.put("p" + i, i == 5 ? 1 : 20); }
        PrizeBook old = new PrizeBook(names, PrizeBook.defaults().schemes).configure("prize-0", "Early 5", 5010, true);
        Map<String, Integer> amounts = new LinkedHashMap<>(); for (PrizeBook.Scheme prize : old.schemes) amounts.put(prize.id, prize.rupees);
        TicketPlan saved = new TicketPlan(sets, amounts, true); assertEquals(101, saved.totalSets());
        saved.validateLive(old.resolve("prize-0", Collections.singletonList("p0"), 5));
        assertTrue(saved.review(old.newRound()).problem(old.newRound()).contains("up to 100"));
    }

}
