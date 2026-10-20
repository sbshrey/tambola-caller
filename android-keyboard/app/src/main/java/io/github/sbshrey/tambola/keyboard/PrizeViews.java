package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

final class PrizeViews {
    static void award(LinearLayout page, GameStore store, String id, Runnable saved, Consumer<String> feedback) {
        Context context = page.getContext(); PrizeBook book = store.prizes(); PrizeBook.Scheme scheme = book.scheme(id);
        Ui.add(page, Ui.text(context, scheme.name, 24), -2);
        if (book.players.isEmpty()) { Ui.add(page, Ui.text(context, "Add player names first. You only need to do this once.", 18), -2); return; }
        if (store.load().count() < scheme.minimumCalls) { Ui.add(page, Ui.text(context, "Call at least " + scheme.minimumCalls + " numbers before awarding this prize.", 18), -2); return; }
        Ui.add(page, Ui.text(context, "Check the paper ticket, then choose one or two winners.", 18), -2);
        List<String> ids = new ArrayList<>(book.players.keySet()); ids.add(0, ""); List<String> labels = new ArrayList<>(); labels.add("No winner selected"); for (String person : book.players.values()) labels.add(person);
        Ui.add(page, Ui.text(context, "First winner", 18), -2); Spinner first = spinner(context, labels); first.setContentDescription("First winner"); Ui.add(page, first, 52);
        Ui.add(page, Ui.text(context, "Second winner (optional)", 18), -2); Spinner second = spinner(context, labels); second.setContentDescription("Second winner"); Ui.add(page, second, 52);
        if (!scheme.winners.isEmpty()) first.setSelection(ids.indexOf(scheme.winners.get(0))); if (scheme.winners.size() == 2) second.setSelection(ids.indexOf(scheme.winners.get(1)));
        int prize = scheme.winners.isEmpty() ? scheme.rupees : scheme.awardedRupees;
        TextView split = Ui.text(context, "", 18); Ui.add(page, split, -2);
        Runnable showSplit = () -> {
            int count = (first.getSelectedItemPosition() == 0 ? 0 : 1) + (second.getSelectedItemPosition() == 0 ? 0 : 1);
            split.setText(count > 0 ? context.getString(R.string.prize_split, PrizeBook.money(prize * 100), PrizeBook.money(prize * 100 / count)) : context.getString(R.string.prize_total, PrizeBook.money(prize * 100)));
        };
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() { public void onItemSelected(AdapterView<?> parent, View view, int position, long value) { showSplit.run(); } public void onNothingSelected(AdapterView<?> parent) {} };
        first.setOnItemSelectedListener(listener); second.setOnItemSelectedListener(listener); showSplit.run();
        Ui.action(page, "Save winners", Ui.GREEN, () -> {
            try {
                List<String> winners = new ArrayList<>(); if (first.getSelectedItemPosition() > 0) winners.add(ids.get(first.getSelectedItemPosition())); if (second.getSelectedItemPosition() > 0) winners.add(ids.get(second.getSelectedItemPosition()));
                if (winners.isEmpty()) throw new IllegalArgumentException("Choose at least one winner.");
                if (new HashSet<>(winners).size() != winners.size()) throw new IllegalArgumentException("Choose two different players.");
                store.prizes(store.prizes().resolve(id, winners, store.load().count())); saved.run();
            } catch (IllegalArgumentException | IllegalStateException error) { feedback.accept(error.getMessage()); }
        });
        if (scheme.bogie()) Ui.add(page, Ui.text(context, book.announcement(scheme), 18), -2);
        if (scheme.resolved()) Ui.action(page, scheme.bogie() ? "Clear Bogie result" : "Clear these winners", Ui.PAPER, () -> {
            page.removeAllViews(); Ui.add(page, Ui.text(context, "Clear result for " + scheme.name + "? Messages already sent stay unchanged.", 20), -2);
            Ui.action(page, "Keep winners", Ui.GREEN, saved);
            Ui.action(page, "Yes, clear winners", Ui.PAPER, () -> { try { store.prizes(store.prizes().award(id, Collections.emptyList(), 0, 0)); saved.run(); } catch (RuntimeException error) { feedback.accept(error.getMessage()); } });
        });
    }
    private static Spinner spinner(Context context, List<String> labels) { Spinner view = new Spinner(context, Spinner.MODE_DROPDOWN); ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); view.setAdapter(adapter); return view; }
}
