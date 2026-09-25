package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.widget.ScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrizeCatalogUiTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class, false, false);
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private GameStore store;
    @Before public void setup() {
        store = new GameStore(context, false); store.save(Game.decode("1,2,3,4,5"));
        Map<String, String> names = new LinkedHashMap<>(); names.put("a", "Asha"); names.put("b", "Bina");
        store.prizes(new PrizeBook(names, PrizeBook.defaults().schemes).award("prize-0", Arrays.asList("a", "b"), 10, 5));
        rule.launchActivity(new Intent().putExtra("section", "catalog")); device.waitForIdle();
    }
    private void tap(BySelector selector) {
        UiObject2 item = device.findObject(selector);
        for (int i = 0; item == null && i < 8; i++) { UiObject2 panel = device.findObject(By.clazz(ScrollView.class)); assertNotNull(panel); panel.scroll(Direction.DOWN, 0.65f); device.waitForIdle(); item = device.findObject(selector); }
        assertNotNull(selector.toString(), item); item.click(); device.waitForIdle();
    }
    @Test public void selectionAndCancelKeepRoundAndExistingWinners() throws Exception {
        tap(By.text("Quick prizes · 9 choices")); tap(By.desc("King")); tap(By.res("android:id/button2")); assertEquals(5, store.prizes().schemes.size());
        tap(By.text("Quick prizes · 9 choices")); assertTrue(device.findObject(By.desc("Early 5")).isChecked()); assertFalse(device.findObject(By.desc("Early 5")).isEnabled());
        tap(By.desc("King")); tap(By.desc("Queen"));
        assertTrue(device.takeScreenshot(new java.io.File(context.getFilesDir(), "prize-choices.png")));
        tap(By.res("android:id/button1"));
        assertTrue(device.wait(Until.gone(By.res("android:id/button1")), 5000));
        assertEquals(7, store.prizes().schemes.size()); assertEquals(500, store.prizes().scheme("prize-0").eachPaise()); assertEquals("1,2,3,4,5", store.load().encode());
        assertTrue(store.prizes().scheme("catalog-king").enabled); assertEquals(10, store.prizes().scheme("catalog-queen").rupees);
    }
    @Test public void addAllConfirmsAndFullCatalogSurvivesStorageRoundTrip() {
        tap(By.text("Add all 41 schemes")); tap(By.res("android:id/button2")); assertEquals(5, store.prizes().schemes.size());
        tap(By.text("Add all 41 schemes")); tap(By.res("android:id/button1"));
        assertTrue(device.wait(Until.hasObject(By.text("Players & prizes")), 5000));
        PrizeBook book = new GameStore(context, false).prizes(); assertEquals(45, book.schemes.size());
        assertTrue(book.scheme("catalog-ninth-line").enabled); assertTrue(book.scheme("catalog-ninth-box").enabled); assertTrue(book.scheme("catalog-house-3").enabled);
        assertEquals(500, book.scheme("prize-0").eachPaise()); assertEquals(2, book.players.size()); assertEquals(5, store.load().count());
        store.newRound(); assertEquals(45, store.prizes().schemes.size()); assertFalse(store.prizes().hasWinners());
    }
}
