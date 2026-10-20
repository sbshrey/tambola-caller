package io.github.sbshrey.tambola.keyboard;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;

final class ResultsViews {
    private static final int WINS = 20, PLAYERS = 12;
    static void table(LinearLayout page, ResultsTable results) {
        LinearLayout section = Ui.column(page.getContext()); Ui.add(page, section, -2); render(section, results, 0, 0);
    }
    private static void render(LinearLayout section, ResultsTable results, int firstWin, int firstPlayer) {
        section.removeAllViews(); int endPlayer = Math.min(firstPlayer + PLAYERS, results.players.size()), maxWins = 0;
        for (int col = firstPlayer; col < endPlayer; col++) maxWins = Math.max(maxWins, results.players.get(col).wins.size());
        int endWin = Math.min(firstWin + WINS, maxWins);
        Ui.add(section, Ui.text(section.getContext(), "Total paid: " + PrizeBook.money(results.summary.totalPaise), 25), -2);
        if (results.unawarded > 0) Ui.add(section, Ui.text(section.getContext(), results.unawarded + " selected schemes not awarded", 18), -2);
        if (results.plan != null) Ui.add(section, Ui.text(section.getContext(), results.balance(), 18), -2);
        Ui.add(section, Ui.text(section.getContext(), "Each player's wins and total\nNames in columns, grouped in threes. Swipe sideways to see all three.", 18), -2);
        if (maxWins > WINS) {
            Ui.add(section, Ui.text(section.getContext(), "Wins " + (firstWin + 1) + "–" + endWin + " per player · totals for full game", 18), -2);
            navigation(section, "Previous wins", "More wins", firstWin > 0, endWin < maxWins,
                () -> render(section, results, Math.max(0, firstWin - WINS), firstPlayer), () -> render(section, results, endWin, firstPlayer));
        }
        if (results.players.size() > PLAYERS) {
            Ui.add(section, Ui.text(section.getContext(), "Players " + (firstPlayer + 1) + "–" + endPlayer + " of " + results.players.size(), 18), -2);
            navigation(section, "Previous players", "More players", firstPlayer > 0, endPlayer < results.players.size(),
                () -> render(section, results, 0, Math.max(0, firstPlayer - PLAYERS)), () -> render(section, results, 0, endPlayer));
        }
        HorizontalScrollView scroll = new HorizontalScrollView(section.getContext()); scroll.setContentDescription("Player results, scroll sideways for names");
        LinearLayout bands = Ui.column(section.getContext()); scroll.addView(bands); Ui.add(section, scroll, -2);
        for (int first = firstPlayer; first < endPlayer; first += 3) {
            int end = Math.min(first + 3, endPlayer); TableLayout grid = new TableLayout(section.getContext());
            TableRow heading = new TableRow(section.getContext()), body = new TableRow(section.getContext()), totals = new TableRow(section.getContext());
            for (int col = first; col < end; col++) {
                ResultsTable.Player player = results.players.get(col); cell(heading, player.label(), true, Ui.PINK, null);
                LinearLayout wins = Ui.column(section.getContext()); wins.setBackgroundColor(Color.WHITE);
                if (player.wins.isEmpty()) Ui.add(wins, Ui.text(section.getContext(), "No prizes won", 18), -2);
                else if (firstWin >= player.wins.size()) Ui.add(wins, Ui.text(section.getContext(), "All wins shown earlier", 18), -2);
                for (int i = firstWin; i < Math.min(firstWin + WINS, player.wins.size()); i++) {
                    ResultsTable.Win win = player.wins.get(i); TextView text = Ui.text(section.getContext(), win.label(), 18);
                    text.setPadding(0, Ui.dp(section.getContext(), 10), 0, Ui.dp(section.getContext(), 10));
                    text.setContentDescription(win.name + ", " + player.name + ", " + PrizeBook.money(win.paise) + (win.shared ? ", shared" : ", winner")); Ui.add(wins, text, -2);
                }
                size(body, wins);
                cell(totals, "TOTAL\n" + PrizeBook.money(player.totalPaise), true, Ui.GREEN, player.name + ", full game total " + PrizeBook.money(player.totalPaise));
            }
            grid.addView(heading); grid.addView(body); grid.addView(totals); Ui.add(bands, grid, -2);
            Ui.add(bands, Ui.text(section.getContext(), "", 8), 16);
        }
        if (!results.summary.bogies.isEmpty()) {
            Ui.add(section, Ui.text(section.getContext(), "Bogies · no payout", 22), -2);
            for (PrizeSummary.Bogie bogie : results.summary.bogies)
                Ui.add(section, Ui.text(section.getContext(), bogie.prize + " · ₹0\n" + String.join(", ", bogie.names), 18), -2);
        }
    }
    private static void navigation(LinearLayout parent, String previous, String next, boolean hasPrevious, boolean hasNext, Runnable back, Runnable forward) {
        LinearLayout row = new LinearLayout(parent.getContext());
        Button a = Ui.button(parent.getContext(), previous, Ui.PAPER, back); a.setEnabled(hasPrevious); Ui.weighted(row, a);
        Button b = Ui.button(parent.getContext(), next, Ui.PAPER, forward); b.setEnabled(hasNext); Ui.weighted(row, b); Ui.add(parent, row, 60);
    }
    private static void cell(TableRow row, String value, boolean bold, int background, String description) {
        TextView cell = Ui.text(row.getContext(), value, 18); cell.setGravity(Gravity.CENTER_VERTICAL); cell.setBackgroundColor(background); cell.setTextColor(bold ? Color.WHITE : Ui.INK);
        if (bold) cell.setTypeface(null, Typeface.BOLD); if (description != null) cell.setContentDescription(description); size(row, cell);
    }
    private static void size(TableRow row, android.view.View cell) {
        int padding = Ui.dp(row.getContext(), 10); cell.setPadding(padding, padding, padding, padding);
        TableRow.LayoutParams size = new TableRow.LayoutParams(Ui.dp(row.getContext(), 190), -1); size.setMargins(1, 1, 1, 1); row.addView(cell, size);
    }
}
