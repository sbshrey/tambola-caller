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
    TicketPlan plan() { return TicketPlanJson.decode(preferences.getString("ticket-plan-v1", null)); }
    String planSnapshot() { return preferences.getString("ticket-plan-v1", ""); }
    void resetPlan() { commit(preferences.edit().remove("ticket-plan-v1")); }
    void resetPrizeDetails() { commit(preferences.edit().putString("prizes-v1", PrizeJson.encode(PrizeBook.defaults())).remove("ticket-plan-v1")); }
    void prizes(PrizeBook book) {
        TicketPlan plan = plan(); SharedPreferences.Editor edit = preferences.edit().putString("prizes-v1", PrizeJson.encode(book));
        if (plan != null) {
            if (load().count() > 0) plan.validateLive(book);
            else edit.putString("ticket-plan-v1", TicketPlanJson.encode(plan.review(book)));
        }
        commit(edit);
    }
    void plan(TicketPlan plan, PrizeBook book, boolean approve) {
        if (load().count() > 0 || prizes().hasWinners()) throw new IllegalArgumentException("Start a new game before changing ticket sales or schemes.");
        TicketPlan next = approve ? plan.approve(book) : plan.review(book);
        commit(preferences.edit().putString("prizes-v1", PrizeJson.encode(book)).putString("ticket-plan-v1", TicketPlanJson.encode(next)));
    }
    void requireReady() { TicketPlan plan = plan(); if (plan != null) plan.validateLive(prizes()); }
    void newRound() {
        TicketPlan plan = plan(); PrizeBook book = prizes().newRound();
        SharedPreferences.Editor edit = preferences.edit().putString("called-v1", "").putString("prizes-v1", PrizeJson.encode(book));
        if (plan != null) edit.putString("ticket-plan-v1", TicketPlanJson.encode(plan.review(book)));
        commit(edit);
    }
    void undo() { Game next = load().undo(); round(next, prizes().afterUndo(next.count())); }
    private void round(Game game, PrizeBook book) { commit(preferences.edit().putString("called-v1", game.encode()).putString("prizes-v1", PrizeJson.encode(book))); }
    void save(Game game) {
        // Persist before inserting. A failed editor handoff can be retried with Insert again.
        commit(preferences.edit().putString("called-v1", game.encode()));
    }
    private void commit(SharedPreferences.Editor edit) { if (!edit.commit()) throw new IllegalStateException("Could not save. Try again."); }
}
