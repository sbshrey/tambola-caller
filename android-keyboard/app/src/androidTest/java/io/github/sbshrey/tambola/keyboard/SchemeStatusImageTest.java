package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SchemeStatusImageTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    static Game game() { StringJoiner calls = new StringJoiner(","); for (int i = 1; i <= 35; i++) calls.add(Integer.toString(i)); return Game.decode(calls.toString()); }
    static PrizeBook selected() { return new PrizeBook(FamilySetup.addTo(PrizeBook.defaults()).players, Collections.emptyList()).addPresets(PrizeCatalog.allIds()); }
    static PrizeBook sample() {
        PrizeBook book = selected(); List<String> ids = new ArrayList<>(book.players.keySet()); List<PrizeBook.Scheme> prizes = new ArrayList<>(book.schemes);
        for (int i = 0; i < 13; i++) {
            List<String> winners = new ArrayList<>(); winners.add(ids.get(i % ids.size()));
            if (i % 4 == 0 || i == 2 || i == 8) winners.add(ids.get((i + 1) % ids.size()));
            if (i == 2 || i == 8) winners.add(ids.get((i + 2) % ids.size()));
            book = book.resolve(prizes.get(i).id, winners, 35);
        }
        return book;
    }
    private void save(SchemeStatusImage image, int index, String name) throws IOException {
        Bitmap bitmap = image.picture(index); assertEquals(SchemeStatusImage.WIDTH, bitmap.getWidth()); assertTrue(bitmap.getHeight() <= SchemeStatusImage.MAX_HEIGHT);
        try (OutputStream output = context.openFileOutput(name + ".png", Context.MODE_PRIVATE)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); }
        finally { bitmap.recycle(); }
    }
    @Test public void fortyTwoSchemesShareOpenAndWonNamesWithReadOnlyImages() throws Exception {
        PrizeBook book = sample(); String saved = PrizeJson.encode(book); SchemeStatusImage image = new SchemeStatusImage(book, game(), null);
        assertEquals(31, image.status.remaining.size()); assertEquals(11, image.status.won.size()); assertEquals(2, image.status.bogies.size()); assertEquals(11000, image.status.paidPaise);
        assertEquals(1, image.pages.size()); Set<String> ids = new HashSet<>();
        for (SchemeStatusImage.Page page : image.pages) for (SchemeStatusImage.Row row : page.rows) for (SchemeStatusImage.Card card : row.cards) {
            assertTrue(ids.add(card.entry.id)); assertEquals(card.entry.detail, card.detail.getText().toString());
        }
        assertEquals(42, ids.size()); save(image, 0, "scheme-status-example");
        List<Uri> files = Sharing.createSchemeStatus(context, game(), book, null); assertEquals(image.pages.size(), files.size());
        assertEquals("image/png", context.getContentResolver().getType(files.get(0)));
        try (InputStream input = context.getContentResolver().openInputStream(files.get(0))) { Bitmap bitmap = BitmapFactory.decodeStream(input); assertEquals(image.pages.get(0).height, bitmap.getHeight()); bitmap.recycle(); }
        assertThrows(FileNotFoundException.class, () -> context.getContentResolver().openFileDescriptor(files.get(0), "w"));
        assertEquals(saved, PrizeJson.encode(book)); assertEquals(35, game().count());
    }
    @Test public void beforeFirstCallAndAllResolvedAreShareableWithoutMissingSchemes() throws Exception {
        PrizeBook book = selected(); SchemeStatusImage before = new SchemeStatusImage(book, Game.empty(), null);
        assertEquals(42, before.status.remaining.size()); assertTrue(before.status.won.isEmpty()); assertEquals("Before the first call", before.progress.getText().toString());
        assertEquals(before.pages.size(), Sharing.createSchemeStatus(context, Game.empty(), book, null).size()); save(before, 0, "scheme-status-before-game");
        for (PrizeBook.Scheme prize : new ArrayList<>(book.schemes)) book = book.resolve(prize.id, Arrays.asList("family-varsha", "family-rekha"), 35);
        SchemeStatusImage complete = new SchemeStatusImage(book, game(), null); assertTrue(complete.status.remaining.isEmpty()); assertEquals(42, complete.status.won.size()); assertEquals(42000, complete.status.paidPaise);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < complete.pages.size(); i++) {
            for (SchemeStatusImage.Row row : complete.pages.get(i).rows) for (SchemeStatusImage.Card card : row.cards) assertTrue(ids.add(card.entry.id));
            Bitmap bitmap = complete.picture(i); assertTrue(bitmap.getHeight() <= SchemeStatusImage.MAX_HEIGHT); bitmap.recycle();
        }
        assertEquals(42, ids.size());
    }
    @Test public void maximumSchemesPaginateAndRepeatHeadingsWithoutDroppingEntries() {
        PrizeBook book = selected(); while (book.schemes.size() < 500) book = book.configure(null, "लंबा पुरस्कार " + book.schemes.size() + " परिवार के साथ तम्बोला खेल", 10, true);
        SchemeStatusImage image = new SchemeStatusImage(book, game(), null); assertTrue(image.pages.size() > 1); Set<String> ids = new HashSet<>();
        for (int i = 0; i < image.pages.size(); i++) {
            SchemeStatusImage.Page page = image.pages.get(i); assertTrue(page.height <= SchemeStatusImage.MAX_HEIGHT); assertFalse(page.rows.isEmpty());
            assertTrue(page.rows.get(0).heading.startsWith("REMAINING"));
            for (SchemeStatusImage.Row row : page.rows) for (SchemeStatusImage.Card card : row.cards) assertTrue(ids.add(card.entry.id));
            Bitmap bitmap = image.picture(i); bitmap.recycle();
        }
        assertEquals(500, ids.size());
    }
    @Test public void maximumLengthBogieNamesContinueWithoutTruncatingAnyone() throws Exception {
        Map<String, String> names = new LinkedHashMap<>(); for (int i = 0; i < 100; i++) names.put("p" + i, String.format(Locale.ROOT, "%02d", i) + "W".repeat(58));
        PrizeBook book = new PrizeBook(names, PrizeBook.defaults().schemes).resolve("prize-0", new ArrayList<>(names.keySet()), 35);
        SchemeStatusImage image = new SchemeStatusImage(book, game(), null); assertTrue(image.pages.size() > 1); StringBuilder detail = new StringBuilder(); int parts = 0;
        for (int i = 0; i < image.pages.size(); i++) {
            SchemeStatusImage.Page page = image.pages.get(i); assertTrue(page.height <= SchemeStatusImage.MAX_HEIGHT); assertFalse(page.rows.isEmpty());
            for (SchemeStatusImage.Row row : page.rows) for (SchemeStatusImage.Card card : row.cards) if (card.entry.state == SchemeStatus.State.BOGIE) {
                detail.append(card.detail.getText()); if (parts++ > 0) assertTrue(card.title.getText().toString().contains("continued"));
            }
            Bitmap bitmap = image.picture(i); bitmap.recycle();
        }
        assertTrue(parts > 1); assertEquals(image.status.bogies.get(0).detail, detail.toString());
        save(image, image.pages.size() - 1, "scheme-status-long-bogie");
    }
}
