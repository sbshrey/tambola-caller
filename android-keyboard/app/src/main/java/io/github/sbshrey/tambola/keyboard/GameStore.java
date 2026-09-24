package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

final class GameStore {
    private final SharedPreferences preferences;
    GameStore(Context context, boolean practice) {
        preferences = context.getSharedPreferences(practice ? "practice-round" : "tambola-round", Context.MODE_PRIVATE);
    }
    Game load() { return Game.decode(preferences.getString("called-v1", "")); }
    void save(Game game) {
        // Persist before inserting. A failed editor handoff can be retried with Insert again.
        if (!preferences.edit().putString("called-v1", game.encode()).commit()) throw new IllegalStateException("Could not save. Try again.");
    }
}
