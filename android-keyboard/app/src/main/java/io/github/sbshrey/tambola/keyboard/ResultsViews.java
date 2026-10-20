package io.github.sbshrey.tambola.keyboard;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;

final class ResultsViews {
    private static final int ROWS = 30, PLAYERS = 12;
    static void table(LinearLayout page, ResultsTable results) { table(page, results, 0, 0); }
    private static void table(LinearLayout page, ResultsTable results, int firstRow, int firstPlayer) {
        LinearLayout section = Ui.column(page.getContext()); Ui.add(page, section, -2);
        render(section, results, firstRow, firstPlayer);
    }
    private static void render(LinearLayout section, ResultsTable results, int firstRow, int firstPlayer) {
        section.removeAllViews(); int endRow = Math.min(firstRow + ROWS, results.rows.size()), endPlayer = Math.min(firstPlayer + PLAYERS, results.players.size());
        Ui.add(section, Ui.text(section.getContext(), "Total paid: " + PrizeBook.money(results.summary.totalPaise), 25), -2);
        if (results.unawarded > 0) Ui.add(section, Ui.text(section.getContext(), results.unawarded + " selected schemes not awarded", 18), -2);
        if (results.plan != null) Ui.add(section, Ui.text(section.getContext(), results.balance(), 18), -2);
        Ui.add(section, Ui.text(section.getContext(), "Schemes ↓ · Players →\nSwipe sideways for players. — = no payout.", 18), -2);
        if (results.rows.size() > ROWS || results.players.size() > PLAYERS) {
            Ui.add(section, Ui.text(section.getContext(), "Schemes " + (firstRow + 1) + "–" + endRow + " of " + results.rows.size() + (results.players.size() > PLAYERS ? " · Players " + (firstPlayer + 1) + "–" + endPlayer + " of " + results.players.size() : ""), 18), -2);
            navigation(section, "Previous schemes", "More schemes", firstRow > 0, endRow < results.rows.size(),
                () -> render(section, results, Math.max(0, firstRow - ROWS), firstPlayer), () -> render(section, results, endRow, firstPlayer));
            if (results.players.size() > PLAYERS) navigation(section, "Previous players", "More players", firstPlayer > 0, endPlayer < results.players.size(),
                () -> render(section, results, firstRow, Math.max(0, firstPlayer - PLAYERS)), () -> render(section, results, firstRow, endPlayer));
        }
        HorizontalScrollView scroll = new HorizontalScrollView(section.getContext()); scroll.setContentDescription("Results table, scroll sideways for players");
        TableLayout grid = new TableLayout(section.getContext()); scroll.addView(grid); Ui.add(section, scroll, -2);
        TableRow heading = new TableRow(section.getContext()); cell(heading, "Scheme / result", 200, true, Ui.PINK, null);
        for (int col = firstPlayer; col < endPlayer; col++) cell(heading, results.players.get(col).label(), 130, true, Ui.PINK, null);
        cell(heading, "Paid\nall players", 110, true, Ui.PINK, null); grid.addView(heading);
        for (int row = firstRow; row < endRow; row++) {
            ResultsTable.Row prize = results.rows.get(row); TableRow line = new TableRow(section.getContext());
            int color = prize.bogie ? Color.rgb(247, 228, 234) : row % 2 == 0 ? Color.WHITE : Ui.PAPER;
            cell(line, prize.label(), 200, false, color, null);
            for (int col = firstPlayer; col < endPlayer; col++) cell(line, prize.share(col), 130, false, color,
                prize.name + ", " + results.players.get(col).name + ", " + PrizeBook.money(prize.shares.get(col)) + ", " + prize.status);
            cell(line, PrizeBook.money(prize.totalPaise), 110, false, color, null); grid.addView(line);
        }
        TableRow totals = new TableRow(section.getContext()); cell(totals, "TOTAL\nFull game", 200, true, Ui.GREEN, null);
        for (int col = firstPlayer; col < endPlayer; col++) cell(totals, PrizeBook.money(results.players.get(col).totalPaise), 130, true, Ui.GREEN, results.players.get(col).name + ", full game total " + PrizeBook.money(results.players.get(col).totalPaise));
        cell(totals, PrizeBook.money(results.summary.totalPaise), 110, true, Ui.GREEN, null); grid.addView(totals);
    }
    private static void navigation(LinearLayout parent, String previous, String next, boolean hasPrevious, boolean hasNext, Runnable back, Runnable forward) {
        LinearLayout row = new LinearLayout(parent.getContext());
        Button a = Ui.button(parent.getContext(), previous, Ui.PAPER, back); a.setEnabled(hasPrevious); Ui.weighted(row, a);
        Button b = Ui.button(parent.getContext(), next, Ui.PAPER, forward); b.setEnabled(hasNext); Ui.weighted(row, b); Ui.add(parent, row, 60);
    }
    private static void cell(TableRow row, String value, int width, boolean bold, int background, String description) {
        TextView cell = Ui.text(row.getContext(), value, 18); cell.setPadding(Ui.dp(row.getContext(), 10), Ui.dp(row.getContext(), 12), Ui.dp(row.getContext(), 10), Ui.dp(row.getContext(), 12));
        cell.setGravity(Gravity.CENTER_VERTICAL); cell.setBackgroundColor(background); cell.setTextColor(bold ? Color.WHITE : Ui.INK);
        if (bold) cell.setTypeface(null, Typeface.BOLD); if (description != null) cell.setContentDescription(description);
        TableRow.LayoutParams size = new TableRow.LayoutParams(Ui.dp(row.getContext(), width), -1); size.setMargins(1, 1, 1, 1); row.addView(cell, size);
    }
}
