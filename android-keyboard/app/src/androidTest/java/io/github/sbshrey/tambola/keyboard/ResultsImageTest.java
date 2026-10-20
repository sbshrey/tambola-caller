package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ResultsImageTest extends KeyboardScenario {
    @Before public void clearTicketPlan() { store.resetPlan(); }
    private PrizeBook sample() {
        PrizeBook book = store.prizes(); List<String> ids = new ArrayList<>(book.players.keySet());
        List<PrizeBook.Scheme> schemes = new ArrayList<>(book.schemes);
        for (int i = 0; i < 11; i++) book = book.resolve(schemes.get(i).id, Collections.singletonList(ids.get(i)), 90);
        book = book.resolve("catalog-queen", Arrays.asList(ids.get(0), ids.get(1)), 90);
        return book.resolve("catalog-t", Arrays.asList(ids.get(0), ids.get(1), ids.get(10)), 90);
    }
    @Test public void familyResultsPaginateWithAllNamesAndGrandTotal() throws Exception {
        PrizeBook book = sample(); ResultsImage image = new ResultsImage(book, 90);
        assertEquals(1, image.pages.size()); assertEquals(11, image.summary.players.size()); assertEquals(11000, image.summary.totalPaise);
        assertEquals(11, image.summary.paidPrizes);
        StringBuilder text = imageText(image);
        for (String name : book.players.values()) assertTrue(text.toString().contains(name));
        assertTrue(text.toString().contains("shared")); assertTrue(text.toString().contains("BOGIE"));
        List<Uri> uris = Sharing.createResults(context, Game.decode("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15"), book);
        assertEquals(image.pages.size(), uris.size()); assertEquals("image/png", context.getContentResolver().getType(uris.get(0)));
        try (InputStream input = context.getContentResolver().openInputStream(uris.get(0))) {
            Bitmap bitmap = BitmapFactory.decodeStream(input); assertEquals(image.pages.get(0).width, bitmap.getWidth()); assertTrue(bitmap.getHeight() <= ResultsImage.MAX_HEIGHT); bitmap.recycle();
        }
        assertThrows(FileNotFoundException.class, () -> context.getContentResolver().openFileDescriptor(uris.get(0), "w"));
        Bitmap preview = image.picture(0);
        try (OutputStream output = context.openFileOutput("results-picture-example.png", Context.MODE_PRIVATE)) { assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        finally { preview.recycle(); }
    }
    @Test public void longHindiNamesAndManyPrizesPaginateWithoutDroppingAnyPlayerOrPrize() throws Exception {
        PrizeBook book = PrizeBook.defaults();
        for (int i = 0; i < 100; i++) book = book.player(null, "खिलाड़ी " + i + " लंबा नाम जिसे पूरा दिखाना ज़रूरी है");
        List<String> names = new ArrayList<>(book.players.keySet());
        for (int i = 5; i < 60; i++) book = book.configure(null, "बहुत लंबा पुरस्कार नाम " + i + " परिवार के साथ तम्बोला खेल", 11, true);
        for (PrizeBook.Scheme scheme : new ArrayList<>(book.schemes)) book = book.resolve(scheme.id, names.subList(0, 2), 90);
        ResultsImage image = new ResultsImage(book, 90); assertTrue(image.pages.size() > 1);
        Set<String> cells = new HashSet<>(); StringBuilder text = new StringBuilder();
        for (int i = 0; i < image.pages.size(); i++) {
            ResultsImage.Page page = image.pages.get(i); assertTrue(page.height <= ResultsImage.MAX_HEIGHT);
            for (ResultsImage.Band band : page.bands) for (ResultsImage.Column column : band.columns) {
                text.append(column.name.getText());
                for (ResultsImage.Entry entry : column.entries) {
                    text.append(entry.label.getText()); assertTrue("No repeated win", cells.add(entry.win.id + ":" + column.player.id));
                }
            }
            Bitmap bitmap = image.picture(i); assertEquals(page.height, bitmap.getHeight());
            if (i == 0 || i == image.pages.size() - 1) try (OutputStream output = context.openFileOutput("results-long-page-" + i + ".png", Context.MODE_PRIVATE)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            bitmap.recycle();
        }
        assertEquals(120, cells.size()); // Only the two winners × 60 schemes, with no blank cells for 98 other players.
        for (String name : book.players.values()) assertTrue(text.toString().contains(name));
        for (PrizeBook.Scheme prize : book.schemes) assertTrue(text.toString().contains(prize.name));
        assertEquals(65500, image.summary.totalPaise);
    }
    @Test public void onePlayerWinningManySchemesUsesOneColumnAndScreenMatchesImage() throws Exception {
        PrizeBook book = store.prizes();
        for (PrizeBook.Scheme prize : new ArrayList<>(book.schemes)) book = book.resolve(prize.id, Collections.singletonList("family-varsha"), 90);
        store.save(Game.decode("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15")); store.prizes(book);
        ResultsImage image = new ResultsImage(book, 90); assertEquals(46000, image.summary.totalPaise); assertEquals(11, image.table.players.size());
        Bitmap bitmap = image.picture(0);
        try (OutputStream output = context.openFileOutput("results-many-wins.png", Context.MODE_PRIVATE)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); } finally { bitmap.recycle(); }
        context.startActivity(new Intent(context, MainActivity.class).putExtra("section", "results").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            visible(By.textContains("Each player's wins and total")); visible(By.text("Total paid: ₹460"));
            androidx.test.uiautomator.UiObject2 table = visible(By.desc("Player results, scroll sideways for names")); assertNotNull(table);
            visible(By.desc("Early 5, Varsha, ₹10, winner"));
            screenshot("results-table-screen");
            tap("More wins"); visible(By.textContains("Wins 21–40"));
            screenshot("results-table-more-wins");
        } finally {
            main(() -> {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitor monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance();
                for (androidx.test.runner.lifecycle.Stage stage : androidx.test.runner.lifecycle.Stage.values())
                    for (android.app.Activity activity : new ArrayList<>(monitor.getActivitiesInStage(stage))) if (activity instanceof MainActivity) activity.finish();
            }); device.waitForIdle();
        }
    }
    private StringBuilder imageText(ResultsImage image) {
        StringBuilder text = new StringBuilder();
        for (ResultsImage.Page page : image.pages) {
            for (ResultsImage.Band band : page.bands) for (ResultsImage.Column column : band.columns) {
                text.append(column.name.getText()); for (ResultsImage.Entry entry : column.entries) text.append(entry.label.getText());
            }
            for (android.text.StaticLayout bogie : page.bogies) text.append(bogie.getText());
        }
        return text;
    }
    @Test public void fortyTwoSchemeResultsFitReadablePlayerColumnsWithExactShares() throws Exception {
        PrizeBook book = new PrizeBook(store.prizes().players, Collections.emptyList()).addPresets(PrizeCatalog.allIds());
        assertEquals(42, book.schemes.size()); List<PrizeBook.Scheme> catalog = new ArrayList<>(book.schemes);
        while (book.schemes.size() < 55) book = book.configure(null, "Extra prize " + book.schemes.size(), 10, true);
        TicketPlan plan = TicketPlan.forPlayers(book).approve(book); List<String> people = new ArrayList<>(book.players.keySet());
        for (int i = 0; i < catalog.size(); i++) {
            List<String> winners = new ArrayList<>(); winners.add(people.get(i % people.size()));
            if (i % 7 == 0 || i == 8 || i == 26) winners.add(people.get((i + 1) % people.size()));
            if (i == 8 || i == 26) winners.add(people.get((i + 2) % people.size()));
            book = book.resolve(catalog.get(i).id, winners, 90);
        }
        ResultsImage image = new ResultsImage(book, 90, plan);
        assertEquals(1, image.pages.size()); assertEquals(40000, image.summary.totalPaise);
        assertEquals(40, image.summary.paidPrizes); assertEquals(2, image.summary.bogies.size()); assertEquals(13, image.table.unawarded);
        Set<String> entries = new HashSet<>(); int total = 0;
        for (ResultsImage.Band band : image.pages.get(0).bands) {
            assertTrue(band.columns.size() <= 3);
            for (ResultsImage.Column column : band.columns) for (ResultsImage.Entry entry : column.entries) {
                PrizeBook.Scheme prize = null; for (PrizeBook.Scheme candidate : book.schemes) if (candidate.id.equals(entry.win.id)) prize = candidate;
                assertNotNull(prize); assertTrue(prize.winners.contains(column.player.id)); assertTrue(entries.add(column.player.id + ":" + entry.win.id)); total += entry.win.paise;
            }
        }
        assertEquals(40000, total); assertEquals(46, entries.size());
        Bitmap bitmap = image.picture(0); assertTrue(bitmap.getHeight() <= ResultsImage.MAX_HEIGHT);
        try (OutputStream output = context.openFileOutput("results-player-columns-example.png", Context.MODE_PRIVATE)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        finally { bitmap.recycle(); }
    }
    @Test public void maximumWinsForOnePlayerContinueWithoutOmissionsOrRepeatedAwards() {
        PrizeBook book = PrizeBook.defaults().player(null, "Varsha"); String player = book.players.keySet().iterator().next();
        while (book.schemes.size() < PrizeBook.MAX_SCHEMES) book = book.configure(null, "Long prize " + book.schemes.size() + " with a name that wraps inside the column", 10, true);
        for (PrizeBook.Scheme prize : new ArrayList<>(book.schemes)) book = book.resolve(prize.id, Collections.singletonList(player), 90);
        ResultsImage image = new ResultsImage(book, 90); assertTrue(image.pages.size() > 1); assertEquals(500000, image.summary.totalPaise);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < image.pages.size(); i++) {
            ResultsImage.Page page = image.pages.get(i); assertTrue(page.height <= ResultsImage.MAX_HEIGHT); assertFalse(page.bands.isEmpty());
            for (ResultsImage.Band band : page.bands) for (ResultsImage.Column column : band.columns) {
                assertEquals(500000, column.player.totalPaise); assertTrue(column.name.getText().toString().contains("Varsha"));
                if (i > 0) assertTrue(column.name.getText().toString().contains("Continued"));
                for (ResultsImage.Entry entry : column.entries) assertTrue(ids.add(entry.win.id));
            }
            Bitmap bitmap = image.picture(i); assertEquals(page.height, bitmap.getHeight()); bitmap.recycle();
        }
        assertEquals(500, ids.size());
    }
    @Test public void bogieOnlyGameKeepsNamesWithZeroTotalsAndNoWinningEntries() {
        PrizeBook book = store.prizes();
        book = book.resolve("prize-0", new ArrayList<>(book.players.keySet()), 5);
        ResultsImage image = new ResultsImage(book, 5); assertEquals(0, image.summary.totalPaise); assertEquals(1, image.summary.bogies.size());
        Set<String> names = new HashSet<>();
        for (ResultsImage.Page page : image.pages) for (ResultsImage.Band band : page.bands) for (ResultsImage.Column column : band.columns) {
            assertTrue(column.entries.isEmpty()); assertEquals(0, column.player.totalPaise); names.add(column.player.name);
        }
        assertEquals(new HashSet<>(book.players.values()), names); assertTrue(imageText(image).toString().contains("BOGIE · Early 5 · ₹0"));
        Bitmap bitmap = image.picture(0); bitmap.recycle();
    }
    @Test public void imageShareIntentsGrantOnlyReadAccessToEveryPage() {
        List<Uri> uris = Arrays.asList(Uri.parse("content://example/one.png"), Uri.parse("content://example/two.png"));
        Intent multi = Sharing.imagesIntent(uris); assertEquals(Intent.ACTION_SEND_MULTIPLE, multi.getAction());
        assertEquals(uris, multi.getParcelableArrayListExtra(Intent.EXTRA_STREAM)); assertEquals(2, multi.getClipData().getItemCount());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, multi.getFlags()); assertTrue(multi.getClipData().getDescription().hasMimeType("image/png"));
        Intent single = Sharing.imagesIntent(uris.subList(0, 1)); assertEquals(Intent.ACTION_SEND, single.getAction()); assertEquals(uris.get(0), single.getParcelableExtra(Intent.EXTRA_STREAM));
        assertThrows(IllegalArgumentException.class, () -> new ResultsImage(store.prizes(), 90));
    }
    @Test public void fundedResultsShowPurchasedSetsAndUnpaidBogieBalance() throws Exception {
        PrizeBook book = store.prizes();
        while (book.schemes.size() < 60) book = book.configure(null, "Extra " + book.schemes.size(), 10, true);
        TicketPlan plan = TicketPlan.forPlayers(book).with("family-varsha", 2).approve(book);
        book = book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 5)
            .resolve("catalog-queen", Arrays.asList("family-varsha", "family-rekha", "family-suman"), 5);
        ResultsImage image = new ResultsImage(book, 90, plan); assertEquals(600, image.plan.collection()); assertEquals(1000, image.summary.totalPaise);
        assertTrue(image.pages.get(0).bands.get(0).columns.get(0).name.getText().toString().contains("2 sets"));
        assertEquals("Collected ₹600 · Unallocated ₹0 · Unpaid prizes ₹590", image.table.balance());
        Bitmap bitmap = image.picture(0);
        try (OutputStream output = context.openFileOutput("results-funded-example.png", Context.MODE_PRIVATE)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        finally { bitmap.recycle(); }
    }
    @Test public void keyboardSharesPictureWithoutDrawingOrChangingAwardsAndProtectsDrafts() {
        store.save(Game.decode("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15")); store.prizes(sample());
        main(() -> rule.getActivity().acceptImages = true); focus(); String saved = PrizeJson.encode(store.prizes());
        main(() -> rule.getActivity().draft.getText().append("Keep this message"));
        tap("Winners"); tap("Share results picture"); expect("Send or clear the current message first.");
        main(() -> assertTrue(rule.getActivity().receivedImages.isEmpty())); assertEquals("Keep this message", draft());
        tap("Call"); tap(By.desc("Clear unsent message")); tap("Yes, clear message"); expect("Message cleared.");
        tap("Winners"); tap("Share results picture"); expect("Results picture ready");
        main(() -> assertEquals(new ResultsImage(store.prizes(), 15).pages.size(), rule.getActivity().receivedImages.size())); assertEquals("", draft());
        assertEquals(15, store.load().count()); assertEquals(saved, PrizeJson.encode(store.prizes()));
        tap("Board"); tap("Start a new game"); tap("Share results picture first"); expect("Results picture ready");
        main(() -> assertEquals(new ResultsImage(store.prizes(), 15).pages.size() * 2, rule.getActivity().receivedImages.size())); assertEquals(15, store.load().count());
    }
}
