package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FeaturesTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class, false, false);
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private PrizeBook people() { LinkedHashMap<String, String> names = new LinkedHashMap<>(); names.put("a", "Asha"); names.put("b", "Bina"); return new PrizeBook(names, PrizeBook.defaults().schemes); }
    @Test public void playbackServiceStartsAndStopsAfterTheClip() throws Exception {
        rule.launchActivity(new Intent().putExtra("section", "home"));
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1), finished = new java.util.concurrent.CountDownLatch(1); AtomicInteger errors = new AtomicInteger();
        android.os.ResultReceiver result = new android.os.ResultReceiver(new android.os.Handler(android.os.Looper.getMainLooper())) { @Override protected void onReceiveResult(int code, Bundle data) { if (code == 0) started.countDown(); if (code == 1) errors.incrementAndGet(); if (code == 2) finished.countDown(); } };
        try {
            context.startForegroundService(new Intent(context, CallPlaybackService.class).putExtra("number", 7).putExtra("language", "en").putExtra("result", result));
            assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS)); assertTrue(finished.await(15, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, errors.get());
        } finally { context.stopService(new Intent(context, CallPlaybackService.class)); }
    }
    @Test public void jsonAndRoundChangesPreserveSetupAndInvalidateOnlyRemovedAwards() {
        GameStore store = new GameStore(context, false); store.save(Game.decode("1,2,3,4,5")); store.prizes(people().award("prize-0", Arrays.asList("a", "b"), 11, 5));
        assertEquals(550, store.prizes().scheme("prize-0").eachPaise());
        store.undo(); assertEquals(4, store.load().count()); assertFalse(store.prizes().hasWinners()); assertEquals(2, store.prizes().players.size());
        store.newRound(); assertEquals(0, store.load().count()); assertEquals(2, store.prizes().players.size());
        context.getSharedPreferences("tambola-round", Context.MODE_PRIVATE).edit().putString("prizes-v1", "invalid").commit();
        store.save(Game.decode("47")); assertThrows(IllegalArgumentException.class, store::newRound); assertEquals("47", store.load().encode());
        store.prizes(PrizeBook.defaults());
    }
    @Test public void picturesAndAudioUseReadOnlyUrisAndCorrectFiles() throws Exception {
        Game game = Game.decode("7,47,90");
        for (String kind : new String[]{Sharing.NUMBER, Sharing.RECENT, Sharing.BOARD}) {
            Uri uri = Sharing.create(context, game, "hi", kind); assertEquals("image/png", context.getContentResolver().getType(uri));
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) { Bitmap bitmap = BitmapFactory.decodeStream(stream); assertNotNull(bitmap); assertEquals(1080, bitmap.getWidth()); assertEquals(kind.equals(Sharing.BOARD) ? 1280 : 1000, bitmap.getHeight());
                try (FileOutputStream output = context.openFileOutput("preview-" + kind + ".png", Context.MODE_PRIVATE)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); } bitmap.recycle(); }
            assertThrows(FileNotFoundException.class, () -> context.getContentResolver().openFileDescriptor(uri, "w"));
        }
        Uri audio = Sharing.create(context, game, "hi", Sharing.AUDIO); assertEquals("audio/mpeg", context.getContentResolver().getType(audio));
        try (InputStream actual = context.getContentResolver().openInputStream(audio); InputStream expected = context.getAssets().open(CallAudio.asset("hi", 90))) { assertEquals(hash(expected), hash(actual)); }
        Uri traversal = Uri.parse("content://" + context.getPackageName() + ".shared/%2e%2e%2fsecrets.mp3");
        assertThrows(FileNotFoundException.class, () -> context.getContentResolver().openInputStream(traversal));
    }
    private String hash(InputStream input) throws Exception { java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192]; int size; while ((size = input.read(buffer)) != -1) digest.update(buffer, 0, size); return Arrays.toString(digest.digest()); }
    @Test public void richContentChecksRecipientSupportBeforeInsertion() throws Exception {
        Uri uri = Sharing.create(context, Game.decode("47"), "en", Sharing.NUMBER); EditorInfo info = new EditorInfo(); AtomicInteger calls = new AtomicInteger();
        InputConnection editor = new BaseInputConnection(new View(context), true) { @Override public boolean commitContent(InputContentInfo content, int flags, Bundle options) { calls.incrementAndGet(); assertEquals(uri, content.getContentUri()); assertEquals(InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, flags); return true; } };
        assertFalse(Sharing.insert(editor, info, uri, "image/png")); assertEquals(0, calls.get());
        info.contentMimeTypes = new String[]{"image/*"}; assertTrue(Sharing.insert(editor, info, uri, "image/png")); assertEquals(1, calls.get());
        assertFalse(Sharing.insert(editor, info, uri, "audio/mpeg")); assertEquals(1, calls.get());
    }
    @Test public void all270ClipsArePackagedAndSampleAudioDecodesInEveryLanguage() throws Exception {
        for (String language : Preferences.LANGUAGES) {
            for (int number = 1; number <= 90; number++) try (InputStream stream = context.getAssets().open(CallAudio.asset(language, number))) { assertTrue(stream.available() > 1000); assertFalse(CallAudio.phrase(context, language, number).isEmpty()); }
            for (int number : new int[]{7, 22, 90}) { MediaPlayer player = new MediaPlayer(); try (android.content.res.AssetFileDescriptor file = context.getAssets().openFd(CallAudio.asset(language, number))) { player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength()); player.prepare(); assertTrue(player.getDuration() > 500); } finally { player.release(); } }
        }
    }
    @Test public void twoWinnerDropdownsShowSplitAndSaveToSharedStore() {
        GameStore store = new GameStore(context, false); store.save(Game.decode("1,2,3,4,5")); store.prizes(people());
        rule.launchActivity(new Intent().putExtra("section", "home")); AtomicInteger saved = new AtomicInteger(); final LinearLayout[] panel = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            panel[0] = Ui.column(rule.getActivity()); panel[0].setBackgroundColor(Ui.PAPER); rule.getActivity().setContentView(panel[0]);
            PrizeViews.award(panel[0], store, "prize-0", false, saved::incrementAndGet, value -> fail(value));
            List<Spinner> selectors = new ArrayList<>(); for (int i = 0; i < panel[0].getChildCount(); i++) if (panel[0].getChildAt(i) instanceof Spinner) selectors.add((Spinner) panel[0].getChildAt(i));
            assertEquals(2, selectors.size()); selectors.get(0).setSelection(1); selectors.get(1).setSelection(2);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(new File(context.getFilesDir(), "preview-winners.png")));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { for (int i = 0; i < panel[0].getChildCount(); i++) { View child = panel[0].getChildAt(i); if (child instanceof Button && ((Button) child).getText().toString().equals("Save winners")) child.performClick(); } });
        assertEquals(1, saved.get()); assertEquals(Arrays.asList("a", "b"), store.prizes().scheme("prize-0").winners); assertEquals(500, store.prizes().scheme("prize-0").eachPaise());
    }
}
