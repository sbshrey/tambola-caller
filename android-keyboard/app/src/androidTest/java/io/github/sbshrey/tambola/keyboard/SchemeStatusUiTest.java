package io.github.sbshrey.tambola.keyboard;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import androidx.test.uiautomator.By;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SchemeStatusUiTest extends KeyboardScenario {
    @Before public void cleanPlan() { store.resetPlan(); }
    @After public void cleanup() {
        main(() -> { ActivityLifecycleMonitor monitor = ActivityLifecycleMonitorRegistry.getInstance();
            for (Stage stage : Stage.values()) for (Activity activity : new ArrayList<>(monitor.getActivitiesInStage(stage))) if (activity instanceof MainActivity) activity.finish(); });
        device.waitForIdle(); store.resetPlan();
    }
    private void prize() { tap("Winners"); tap("Tap prize + winner"); tap(By.desc("Choose prize: Early 5")); }
    private int received() { int[] count = {0}; main(() -> count[0] = rule.getActivity().receivedImages.size()); return count[0]; }
    private void shareAndCheck() throws Exception {
        int before = received(); String saved = PrizeJson.encode(store.prizes()), round = store.load().encode(), plan = store.planSnapshot();
        tap("Share scheme status picture"); await("Status image inserted", () -> received() == before + 1);
        Uri[] uri = {null}; main(() -> uri[0] = rule.getActivity().receivedImages.get(before));
        try (InputStream input = context.getContentResolver().openInputStream(uri[0])) {
            Bitmap actual = BitmapFactory.decodeStream(input), expected = new SchemeStatusImage(store.prizes(), store.load(), store.plan()).picture(0);
            try { assertTrue("Shared PNG matches the current saved game", expected.sameAs(actual)); } finally { actual.recycle(); expected.recycle(); }
        }
        assertEquals(saved, PrizeJson.encode(store.prizes())); assertEquals(round, store.load().encode()); assertEquals(plan, store.planSnapshot());
    }
    @Test public void claimAnnouncementCorrectionAndNextGameProduceFreshPicturesAndProtectDrafts() throws Exception {
        store.prizes(SchemeStatusImageTest.selected()); store.save(Game.decode("1,2,3,4,5")); main(() -> rule.getActivity().acceptImages = true); focus();
        prize(); tap("Claim for Varsha"); expect("Claim ready"); assertFalse(store.prizes().hasWinners());
        int count = received(); tap("Share scheme status picture"); expect("Send or clear the current message first."); assertEquals(count, received()); assertTrue(draft().contains("claims Early 5"));
        send(); tap("Winners"); shareAndCheck(); assertEquals(42, new SchemeStatus(store.prizes(), null).remaining.size());
        prize(); tap(By.desc("Varsha, choose claimant")); tap(By.desc("Rekha, choose claimant")); tap("Insert winner announcement"); expect("Winner saved");
        assertTrue(draft().contains("₹5 each")); tap("Share scheme status picture"); expect("Send or clear the current message first."); assertEquals(count + 1, received());
        send(); tap("Winners"); shareAndCheck(); assertEquals(41, new SchemeStatus(store.prizes(), null).remaining.size());
        main(() -> store.prizes(store.prizes().resolve("prize-0", Arrays.asList("family-varsha", "family-rekha", "family-suman"), 5)));
        tap("Call"); tap("Share…"); shareAndCheck(); assertEquals(1, new SchemeStatus(store.prizes(), null).bogies.size()); screenshot("scheme-status-share-menu");
        store.newRound(); focus(); tap("Winners"); shareAndCheck(); assertEquals(42, new SchemeStatus(store.prizes(), null).remaining.size()); assertEquals(0, store.load().count());
    }
    @Test public void appSharesBeforeAnyWinAndSharesEveryPageOfALargeUpdate() throws Exception {
        store.prizes(SchemeStatusImageTest.selected()); Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation(); AtomicReference<Intent> shared = new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_CHOOSER.equals(intent.getAction())) return null;
                shared.set(new Intent(intent)); return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        instrumentation.addMonitor(monitor);
        try {
            context.startActivity(new Intent(context, MainActivity.class).putExtra("section", "results").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            tap("Share scheme status picture"); await("Pre-game image chooser opened", () -> shared.get() != null);
            Intent single = shared.get().getParcelableExtra(Intent.EXTRA_INTENT, Intent.class); assertEquals(Intent.ACTION_SEND, single.getAction()); assertEquals("image/png", single.getType());
            assertEquals(0, store.load().count()); assertFalse(store.prizes().hasWinners()); screenshot("scheme-status-app-before-game");
            PrizeBook many = store.prizes(); while (many.schemes.size() < 100) many = many.configure(null, "Long named scheme " + many.schemes.size() + " for the family", 10, true);
            store.prizes(many); store.save(SchemeStatusImageTest.game()); String saved = PrizeJson.encode(store.prizes()); shared.set(null);
            tap("Share scheme status picture"); await("Multiple-image chooser opened", () -> shared.get() != null);
            Intent multi = shared.get().getParcelableExtra(Intent.EXTRA_INTENT, Intent.class); assertEquals(Intent.ACTION_SEND_MULTIPLE, multi.getAction());
            ArrayList<Uri> files = multi.getParcelableArrayListExtra(Intent.EXTRA_STREAM); assertNotNull(files);
            assertEquals(new SchemeStatusImage(store.prizes(), store.load(), null).pages.size(), files.size()); assertEquals(files.size(), multi.getClipData().getItemCount());
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, multi.getFlags()); assertEquals(saved, PrizeJson.encode(store.prizes())); assertEquals(35, store.load().count());
            for (Uri file : files) try (InputStream input = context.getContentResolver().openInputStream(file)) { Bitmap bitmap = BitmapFactory.decodeStream(input); assertNotNull(bitmap); bitmap.recycle(); }
        } finally { instrumentation.removeMonitor(monitor); }
    }
    @Test public void unconfirmedFundedSetupCannotShareAnIncorrectSchemeList() {
        PrizeBook book = new PrizeBook(Collections.singletonMap("v", "Varsha"), PrizeBook.defaults().schemes);
        store.plan(TicketPlan.forPlayers(book), book, false); main(() -> rule.getActivity().acceptImages = true); focus();
        tap("Winners"); tap("Share scheme status picture"); expect("Review tickets and schemes"); assertEquals(0, received());
        assertEquals(0, store.load().count()); assertFalse(store.plan().approved);
        store.plan(store.plan(), store.prizes(), true); tap("Call"); tap("Share…"); tap("Share scheme status picture");
        await("Confirmed setup can share before the first call", () -> received() == 1); assertEquals(0, store.load().count());
    }
}
