package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Large, keyboard-only controls; choosing a name does not save an award. */
final class QuickWinnerViews {
    static void choose(LinearLayout page, PrizeBook book, PrizeBook.Scheme scheme, int calls, TicketPlan ticketPlan,
                       Consumer<List<String>> insert, Runnable review) {
        Context context = page.getContext();
        Ui.add(page, Ui.text(context, scheme.name, 23), -2);
        if (calls < scheme.minimumCalls) {
            Ui.add(page, Ui.text(context, "Call at least " + scheme.minimumCalls + " numbers before awarding this prize.", 18), -2);
            return;
        }
        if (book.players.isEmpty()) {
            Ui.add(page, Ui.text(context, "Go back and add the family list, or add names under Players & prize amounts.", 18), -2);
            return;
        }
        List<String> selected = new ArrayList<>(scheme.bogie() ? scheme.bogiePlayers : scheme.winners);
        TextView preview = Ui.text(context, "", 18);
        preview.setContentDescription("Winner announcement preview");
        preview.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Ui.add(page, preview, -2);
        Button announce = Ui.action(page, "Insert winner announcement",
            Ui.GREEN, () -> insert.accept(new ArrayList<>(selected)));
        Ui.add(page, Ui.text(context, "Tap all confirmed claimants. 1 wins · 2 split · 3+ Bogie, no payout. Tap again to remove.", 17), -2);
        Map<String, Button> buttons = new LinkedHashMap<>();
        Runnable refresh = () -> {
            announce.setEnabled(!selected.isEmpty());
            announce.setText(selected.size() >= 3 ? R.string.insert_bogie : scheme.resolved() ? R.string.update_winner : R.string.insert_winner);
            if (selected.isEmpty()) preview.setText(R.string.choose_winner_below);
            else {
                PrizeBook proposed = book.resolve(scheme.id, selected, calls);
                preview.setText(proposed.announcement(proposed.scheme(scheme.id)));
            }
            buttons.forEach((id, button) -> {
                boolean chosen = selected.contains(id);
                button.setText(chosen ? context.getString(R.string.selected_winner, book.players.get(id)) : book.players.get(id));
                button.setSelected(chosen);
                button.setContentDescription(book.players.get(id) + (chosen ? ", selected claimant" : ", choose claimant"));
            });
        };
        LinearLayout row = null;
        for (Map.Entry<String, String> player : book.players.entrySet()) {
            if (buttons.size() % 2 == 0) { row = new LinearLayout(context); Ui.add(page, row, -2); }
            Button button = Ui.button(context, player.getValue(), Ui.PAPER, () -> {
                if (selected.contains(player.getKey())) selected.remove(player.getKey());
                else selected.add(player.getKey());
                refresh.run();
            });
            button.setEnabled(ticketPlan == null || ticketPlan.sets(player.getKey()) > 0);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            size.setMargins(Ui.dp(context, 3), 0, Ui.dp(context, 3), 0);
            row.addView(button, size); buttons.put(player.getKey(), button);
        }
        Ui.action(page, "Review announcement ↑", Ui.PAPER, review);
        refresh.run();
    }
}
