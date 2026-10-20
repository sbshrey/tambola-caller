package io.github.sbshrey.tambola.keyboard;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrizeJsonTest {
    @Test public void legacyPrizeBookUpgradesWithoutLosingAwardsOrPlayers() throws Exception {
        PrizeBook original = FamilySetup.addTo(PrizeBook.defaults()).award("prize-0", Arrays.asList("family-varsha", "family-rekha"), 11, 5);
        JSONObject legacy = new JSONObject(PrizeJson.encode(original)).put("version", 1);
        JSONArray schemes = legacy.getJSONArray("schemes"); for (int i = 0; i < schemes.length(); i++) schemes.getJSONObject(i).remove("bogie");
        PrizeBook upgraded = PrizeJson.decode(legacy.toString());
        assertEquals(original.players, upgraded.players); assertEquals(550, upgraded.scheme("prize-0").eachPaise());
        assertEquals(5, upgraded.scheme("prize-0").callCount); assertEquals(46, upgraded.schemes.size());
        assertEquals(PrizeJson.encode(original), PrizeJson.encode(upgraded));
    }
    @Test public void bogieSurvivesStorageReopenAndNewRoundClearsIt() {
        GameStore store = new GameStore(InstrumentationRegistry.getInstrumentation().getTargetContext(), false);
        store.save(Game.decode("1,2,3,4,5"));
        PrizeBook book = FamilySetup.addTo(PrizeBook.defaults()).resolve("prize-0", Arrays.asList("family-varsha", "family-rekha", "family-suman"), 5);
        store.prizes(book);
        PrizeBook loaded = new GameStore(InstrumentationRegistry.getInstrumentation().getTargetContext(), false).prizes();
        assertEquals(PrizeJson.encode(book), PrizeJson.encode(loaded)); assertTrue(loaded.scheme("prize-0").bogie());
        assertFalse(loaded.results(5).contains("Total prizes")); store.newRound();
        assertFalse(store.prizes().hasWinners()); assertEquals(11, store.prizes().players.size()); assertEquals(46, store.prizes().schemes.size());
    }
    @Test public void malformedBogieDataIsRejected() throws Exception {
        PrizeBook base = FamilySetup.addTo(PrizeBook.defaults());
        for (JSONArray claims : Arrays.asList(new JSONArray(Arrays.asList("family-varsha")), new JSONArray(Arrays.asList("family-varsha", "family-rekha", "missing")), new JSONArray(Arrays.asList("family-varsha", "family-varsha", "family-suman")))) {
            JSONObject root = new JSONObject(PrizeJson.encode(base)); root.getJSONArray("schemes").getJSONObject(0).put("bogie", claims).put("count", 5);
            assertThrows(IllegalArgumentException.class, () -> PrizeJson.decode(root.toString()));
        }
    }
}
