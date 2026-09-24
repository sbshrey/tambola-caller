package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

final class GameStore {
    private final SharedPreferences preferences;
    GameStore(Context context, boolean practice) {
        preferences = context.getSharedPreferences(practice ? "practice-round" : "tambola-round", Context.MODE_PRIVATE);
    }
    Game load() { return Game.decode(preferences.getString("called-v1", "")); }
    void observe(SharedPreferences.OnSharedPreferenceChangeListener listener) { preferences.registerOnSharedPreferenceChangeListener(listener); }
    void stopObserving(SharedPreferences.OnSharedPreferenceChangeListener listener) { preferences.unregisterOnSharedPreferenceChangeListener(listener); }
    PrizeBook prizes() { return PrizeJson.decode(preferences.getString("prizes-v1", null)); }
    void prizes(PrizeBook book) { commit(preferences.edit().putString("prizes-v1", PrizeJson.encode(book))); }
    void newRound() { round(Game.empty(), prizes().newRound()); }
    void undo() { Game next = load().undo(); round(next, prizes().afterUndo(next.count())); }
    private void round(Game game, PrizeBook book) { commit(preferences.edit().putString("called-v1", game.encode()).putString("prizes-v1", PrizeJson.encode(book))); }
    void save(Game game) {
        // Persist before inserting. A failed editor handoff can be retried with Insert again.
        commit(preferences.edit().putString("called-v1", game.encode()));
    }
    private void commit(SharedPreferences.Editor edit) { if (!edit.commit()) throw new IllegalStateException("Could not save. Try again."); }
}
