package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

final class PrizeViews {
    static void award(LinearLayout page, GameStore store, String id, boolean editAmount, Runnable saved, Consumer<String> feedback) {
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
        EditText amount = new EditText(context); amount.setText(String.format(Locale.ROOT, "%d", prize)); amount.setTextSize(20); amount.setInputType(InputType.TYPE_CLASS_NUMBER); amount.setPrivateImeOptions("tambola-settings"); amount.setContentDescription("Total prize in rupees");
        if (editAmount) { Ui.add(page, Ui.text(context, "Total prize in rupees", 18), -2); Ui.add(page, amount, 56); }
        TextView split = Ui.text(context, "", 18); Ui.add(page, split, -2);
        Runnable showSplit = () -> {
            int count = (first.getSelectedItemPosition() == 0 ? 0 : 1) + (second.getSelectedItemPosition() == 0 ? 0 : 1);
            try { int total = PrizeBook.amount(Integer.parseInt(amount.getText().toString())); split.setText(count > 0 ? context.getString(R.string.prize_split, PrizeBook.money(total * 100), PrizeBook.money(total * 100 / count)) : context.getString(R.string.prize_total, PrizeBook.money(total * 100))); }
            catch (RuntimeException error) { split.setText(R.string.prize_amount_invalid); }
        };
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() { public void onItemSelected(AdapterView<?> parent, View view, int position, long value) { showSplit.run(); } public void onNothingSelected(AdapterView<?> parent) {} };
        first.setOnItemSelectedListener(listener); second.setOnItemSelectedListener(listener); showSplit.run();
        amount.addTextChangedListener(new android.text.TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int c, int f) {} public void onTextChanged(CharSequence s, int a, int b, int c) { showSplit.run(); } public void afterTextChanged(android.text.Editable e) {} });
        Ui.action(page, "Save winners", Ui.GREEN, () -> {
            try {
                List<String> winners = new ArrayList<>(); if (first.getSelectedItemPosition() > 0) winners.add(ids.get(first.getSelectedItemPosition())); if (second.getSelectedItemPosition() > 0) winners.add(ids.get(second.getSelectedItemPosition()));
                if (winners.isEmpty()) throw new IllegalArgumentException("Choose at least one winner.");
                if (new HashSet<>(winners).size() != winners.size()) throw new IllegalArgumentException("Choose two different players.");
                store.prizes(store.prizes().award(id, winners, Integer.parseInt(amount.getText().toString()), store.load().count())); saved.run();
            } catch (NumberFormatException error) { feedback.accept("Enter a whole-rupee prize from 0 to 100000."); }
            catch (IllegalArgumentException | IllegalStateException error) { feedback.accept(error.getMessage()); }
        });
        if (!scheme.winners.isEmpty()) Ui.action(page, "Clear these winners", Ui.PAPER, () -> {
            page.removeAllViews(); Ui.add(page, Ui.text(context, "Clear winners for " + scheme.name + "? Messages already sent stay unchanged.", 20), -2);
            Ui.action(page, "Keep winners", Ui.GREEN, saved);
            Ui.action(page, "Yes, clear winners", Ui.PAPER, () -> { try { store.prizes(store.prizes().award(id, Collections.emptyList(), 0, 0)); saved.run(); } catch (RuntimeException error) { feedback.accept(error.getMessage()); } });
        });
    }
    private static Spinner spinner(Context context, List<String> labels) { Spinner view = new Spinner(context, Spinner.MODE_DROPDOWN); ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); view.setAdapter(adapter); return view; }
}
