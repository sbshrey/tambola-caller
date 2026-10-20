package io.github.sbshrey.tambola.keyboard;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TicketPlanTest {
    private PrizeBook book() { return FamilySetup.addTo(PrizeBook.defaults()); }
    @Test public void mixedSetsChargeFiftyPerSixTicketsAndExcludeNonParticipants() {
        TicketPlan plan = TicketPlan.forPlayers(book()).with("family-varsha", 2).with("family-suman", 0);
        assertEquals(11, plan.totalSets()); assertEquals(66, plan.totalSets() * TicketPlan.TICKETS); assertEquals(550, plan.collection()); assertEquals(10, plan.players());
        assertEquals(2, plan.sets("family-varsha")); assertEquals(0, plan.sets("family-suman"));
        assertThrows(IllegalArgumentException.class, () -> plan.with("family-varsha", -1)); assertThrows(IllegalArgumentException.class, () -> plan.with("family-varsha", 21));
    }
    @Test public void approvalRequiresTheWholeCollectionAndEqualSplitNeverLosesRupees() {
        PrizeBook book = book(); TicketPlan plan = TicketPlan.forPlayers(book());
        assertNotNull(plan.problem(book)); assertThrows(IllegalArgumentException.class, () -> plan.approve(book));
        PrizeBook balanced = plan.balance(book); assertEquals(550, TicketPlan.budget(balanced)); assertNull(plan.problem(balanced));
        TicketPlan approved = plan.approve(balanced); assertTrue(approved.approved); approved.validateLive(balanced);
        int low = 0, high = 0; for (PrizeBook.Scheme prize : balanced.schemes) if (prize.rupees == 11) low++; else if (prize.rupees == 12) high++;
        assertEquals(2, low); assertEquals(44, high);
        PrizeBook tooMuch = balanced.configure("prize-0", "Early 5", 1000, true); assertTrue(plan.problem(tooMuch).contains("Reduce prizes"));
    }
    @Test public void aLivePlanLocksAmountsButAllowsWinsSharesBogiesAndCorrections() {
        TicketPlan draft = TicketPlan.forPlayers(book()).with("family-suman", 0); PrizeBook book = draft.balance(book()); TicketPlan plan = draft.approve(book);
        PrizeBook winners = book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 5); plan.validateLive(winners);
        plan.validateLive(book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha", "family-nirmala"), 5));
        assertThrows(IllegalArgumentException.class, () -> plan.validateLive(book.resolve("prize-0", Collections.singletonList("family-suman"), 5)));
        assertThrows(IllegalArgumentException.class, () -> plan.validateLive(book.award("prize-0", Collections.singletonList("family-varsha"), 999, 5)));
        PrizeBook changed = book.configure("prize-0", "Early 5", book.scheme("prize-0").rupees + 1, true)
            .configure("catalog-king", "King", book.scheme("catalog-king").rupees - 1, true);
        assertThrows(IllegalArgumentException.class, () -> plan.validateLive(changed));
        plan.validateLive(winners.player("family-varsha", "Varsha ji"));
    }
    @Test public void reviewingNextGameKeepsPurchasesButRequiresApprovalAgain() {
        TicketPlan plan = TicketPlan.forPlayers(book()).with("family-varsha", 2); PrizeBook balanced = plan.balance(book());
        TicketPlan next = plan.approve(balanced).review(balanced.newRound()); assertFalse(next.approved); assertEquals(2, next.sets("family-varsha"));
        assertThrows(IllegalArgumentException.class, () -> next.validateLive(balanced));
        TicketPlan added = next.review(balanced.player(null, "New player")); assertEquals(13, added.totalSets());
    }
    @Test public void noEntriesAndNoSelectedSchemesCannotStartOrAllocate() {
        PrizeBook book = book(); TicketPlan zero = TicketPlan.forPlayers(book()); for (String id : book.players.keySet()) zero = zero.with(id, 0);
        assertTrue(zero.problem(book).contains("at least one purchased")); TicketPlan empty = zero;
        assertThrows(IllegalArgumentException.class, () -> empty.balance(book));
        PrizeBook none = book; for (PrizeBook.Scheme prize : book.schemes) none = none.configure(prize.id, prize.name, prize.rupees, false);
        assertTrue(TicketPlan.forPlayers(book).problem(none).contains("at least one prize"));
    }
}
