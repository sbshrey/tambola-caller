package io.github.sbshrey.tambola.keyboard;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CallCorrectionTest extends KeyboardScenario {
    @Test public void accidentalCallCanBeCancelledThenUndoneAndRemovedWithoutLeavingKeyboard() {
        assertFalse(visible(By.text("Undo last number")).isEnabled());
        int number = draw(); screenshot("call-correction-controls");
        tap("Undo last number"); expect("Undo number " + number + "?"); tap("Keep number");
        assertEquals(1, store.load().count()); assertEquals(Game.emoji(number), draft());
        tap("Undo last number"); screenshot("undo-number-confirmation"); tap("Yes, undo number");
        expect("Number " + number + " undone."); await("Number draft cleared", () -> draft().isEmpty());
        assertEquals(0, store.load().count()); assertFalse(visible(By.text("Undo last number")).isEnabled());
        draw(); assertEquals(1, store.load().count()); screenshot("call-after-undo");
    }
    @Test public void alwaysAvailableClearRemovesSelectedDraftButKeepsCallAndResults() {
        int number = draw(); String saved = PrizeJson.encode(store.prizes());
        main(() -> rule.getActivity().draft.selectAll());
        tap(By.desc("Clear unsent message")); tap("Keep message"); assertEquals(Game.emoji(number), draft());
        tap(By.desc("Clear unsent message")); tap("Yes, clear message"); expect("Message cleared.");
        assertEquals("", draft()); assertEquals(1, store.load().count()); assertEquals(saved, PrizeJson.encode(store.prizes()));
        tap(By.desc("Insert current number again")); await("Reinsert same call", () -> draft().equals(Game.emoji(number)));
        assertEquals(1, store.load().count());
    }
    @Test public void undoPreservesAnUnrelatedDraftAndEarlierAwardsButClearsLatestResults() {
        store.save(Game.decode("1,2,3,4,5,6"));
        store.prizes(store.prizes().resolve("prize-0", Collections.singletonList("family-varsha"), 5)
            .resolve("catalog-king", Arrays.asList("family-varsha", "family-rekha"), 6)
            .resolve("catalog-queen", Arrays.asList("family-varsha", "family-rekha", "family-suman"), 6));
        main(() -> { rule.getActivity().draft.setText("Please check this ticket " + Game.emoji(6)); rule.getActivity().draft.selectAll(); }); focus();
        String other = draft(); tap("Undo last number"); tap("Yes, undo number"); expect("Other draft text kept.");
        assertEquals(other, draft()); assertEquals(5, store.load().count()); assertTrue(store.prizes().scheme("prize-0").resolved());
        assertFalse(store.prizes().scheme("catalog-king").resolved()); assertFalse(store.prizes().scheme("catalog-queen").resolved());
    }
    @Test public void selectedNumberAndCursorInsideNumberAreFullyRemoved() {
        int number = draw(); main(() -> rule.getActivity().draft.selectAll());
        tap("Undo last number"); tap("Yes, undo number"); expect("Unsent number cleared."); assertEquals("", draft());
        draw(); main(() -> rule.getActivity().draft.setSelection(1));
        tap("Undo last number"); tap("Yes, undo number"); expect("Unsent number cleared."); assertEquals("", draft()); assertEquals(0, store.load().count());
    }
    @Test public void sentCallCanBeUndoneWithoutChangingSentMessages() {
        int number = draw(); send(); tap("Undo last number"); tap("Yes, undo number"); expect("Ready to continue.");
        assertEquals(0, store.load().count()); assertEquals("", draft());
        main(() -> assertEquals(Collections.singletonList(Game.emoji(number)), rule.getActivity().sentMessages));
    }
    @Test public void changedRoundOrPrizesInvalidatesUndoConfirmation() {
        draw(); tap("Undo last number"); expect("Undo number " + store.load().latest() + "?");
        main(() -> store.prizes(store.prizes().configure("catalog-king", "King", 25, true)));
        tap("Yes, undo number"); expect("Game or prizes changed."); assertEquals(1, store.load().count());
        tap("Undo last number"); expect("Undo number " + store.load().latest() + "?"); main(() -> store.save(store.load().draw(new Random(5)))); device.waitForIdle();
        tap("Yes, undo number"); expect("Game or prizes changed."); assertEquals(2, store.load().count());
    }
    @Test public void rejectedDeletionDoesNotUndoOrPretendTheDraftWasCleared() {
        int number = draw(); main(() -> rule.getActivity().rejectDelete = true);
        tap("Undo last number"); tap("Yes, undo number"); expect("The number is still called.");
        assertEquals(1, store.load().count()); assertEquals(Game.emoji(number), draft());
        tap("Keep number"); tap(By.desc("Clear unsent message")); tap("Yes, clear message");
        expect("clear the message with your normal keyboard"); assertEquals(Game.emoji(number), draft()); assertEquals(1, store.load().count());
        main(() -> rule.getActivity().rejectDelete = false); tap("Undo last number"); tap("Yes, undo number");
        expect("Unsent number cleared."); assertEquals(0, store.load().count()); assertEquals("", draft());
    }
    @Test public void practiceUndoLeavesLiveGameAndPrizesUntouched() {
        store.save(Game.decode("1,2,3,4,5")); store.prizes(store.prizes().resolve("prize-0", Collections.singletonList("family-varsha"), 5));
        String prizes = PrizeJson.encode(store.prizes());
        main(() -> rule.getActivity().draft.setPrivateImeOptions(TambolaInputMethod.PRACTICE)); focus();
        tap(By.desc("Next number")); expect("PRACTICE\n1 / 90");
        tap("Undo last number"); tap("Yes, undo number"); expect("Unsent number cleared.");
        assertEquals(0, new GameStore(context, true).load().count()); assertEquals(5, store.load().count());
        assertEquals(prizes, PrizeJson.encode(store.prizes())); assertEquals("", draft());
    }
}
