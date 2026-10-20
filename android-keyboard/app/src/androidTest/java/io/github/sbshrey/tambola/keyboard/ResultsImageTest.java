package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ResultsImageTest extends KeyboardScenario {
    private PrizeBook sample() {
        PrizeBook book = store.prizes(); List<String> ids = new ArrayList<>(book.players.keySet());
        List<PrizeBook.Scheme> schemes = new ArrayList<>(book.schemes);
        for (int i = 0; i < 11; i++) book = book.resolve(schemes.get(i).id, Collections.singletonList(ids.get(i)), 90);
        book = book.resolve("catalog-queen", Arrays.asList(ids.get(0), ids.get(1)), 90);
        return book.resolve("catalog-t", Arrays.asList(ids.get(0), ids.get(1), ids.get(10)), 90);
    }
    @Test public void familyResultsFitOneReadableImageWithAllNamesAndGrandTotal() throws Exception {
        PrizeBook book = sample(); ResultsImage image = new ResultsImage(book, 90);
        assertEquals(1, image.pages.size()); assertEquals(11, image.summary.players.size()); assertEquals(11000, image.summary.totalPaise);
        assertEquals(12, image.pages.get(0).rows.size());
        StringBuilder text = new StringBuilder(); for (ResultsImage.Row row : image.pages.get(0).rows) text.append(row.name.getText()).append(row.prizes.getText()).append(row.total.getText());
        for (String name : book.players.values()) assertTrue(text.toString().contains(name));
        assertTrue(text.toString().contains("(shared)")); assertTrue(text.toString().contains("Bogie"));
        List<Uri> uris = Sharing.createResults(context, Game.decode("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15"), book);
        assertEquals(1, uris.size()); assertEquals("image/png", context.getContentResolver().getType(uris.get(0)));
        try (InputStream input = context.getContentResolver().openInputStream(uris.get(0))) {
            Bitmap bitmap = BitmapFactory.decodeStream(input); assertEquals(1080, bitmap.getWidth()); assertTrue(bitmap.getHeight() <= ResultsImage.MAX_HEIGHT); bitmap.recycle();
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
        int rows = 0; StringBuilder text = new StringBuilder();
        for (int i = 0; i < image.pages.size(); i++) {
            ResultsImage.Page page = image.pages.get(i); assertTrue(page.height <= ResultsImage.MAX_HEIGHT);
            for (ResultsImage.Row row : page.rows) { rows++; text.append(row.name.getText()).append(row.prizes.getText()); }
            Bitmap bitmap = image.picture(i); assertEquals(page.height, bitmap.getHeight());
            if (i == 0 || i == image.pages.size() - 1) try (OutputStream output = context.openFileOutput("results-long-page-" + i + ".png", Context.MODE_PRIVATE)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            bitmap.recycle();
        }
        assertEquals(110, rows); // Two players have six chunks each; 98 have no prizes.
        for (String name : book.players.values()) assertTrue(text.toString().contains(name));
        for (PrizeBook.Scheme prize : book.schemes) assertTrue(text.toString().contains(prize.name));
        assertEquals(65500, image.summary.totalPaise);
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
        for (PrizeBook.Scheme prize : book.schemes) book = book.configure(prize.id, prize.name, prize.id.equals("prize-0") ? 500 : 100,
            prize.id.equals("prize-0") || prize.id.equals("catalog-queen"));
        TicketPlan plan = TicketPlan.forPlayers(book).with("family-varsha", 2).approve(book);
        book = book.resolve("prize-0", Arrays.asList("family-varsha", "family-rekha"), 5)
            .resolve("catalog-queen", Arrays.asList("family-varsha", "family-rekha", "family-suman"), 5);
        ResultsImage image = new ResultsImage(book, 90, plan); assertEquals(600, image.plan.collection()); assertEquals(50000, image.summary.totalPaise);
        assertTrue(image.pages.get(0).rows.get(0).name.getText().toString().contains("2 sets"));
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
        main(() -> assertEquals(1, rule.getActivity().receivedImages.size())); assertEquals("", draft());
        assertEquals(15, store.load().count()); assertEquals(saved, PrizeJson.encode(store.prizes()));
        tap("Board"); tap("Start a new game"); tap("Share results picture first"); expect("Results picture ready");
        main(() -> assertEquals(2, rule.getActivity().receivedImages.size())); assertEquals(15, store.load().count());
    }
}
