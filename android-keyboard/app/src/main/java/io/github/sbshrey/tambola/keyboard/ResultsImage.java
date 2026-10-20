package io.github.sbshrey.tambola.keyboard;

import android.graphics.*;
import android.text.*;
import java.util.*;

/** Three readable player columns per band, with only their wins and full-game totals. */
final class ResultsImage {
    static final int MAX_HEIGHT = 4096, MARGIN = 32, GAP = 16, FOOTER = 90, TOTAL = 86;
    static final class Entry {
        final ResultsTable.Win win;
        final StaticLayout label;
        final int height;
        Entry(ResultsTable.Win win, int width) { this.win = win; label = layout(win.label(), 30, false, width - 36); height = label.getHeight() + 28; }
    }
    static final class Column {
        final ResultsTable.Player player;
        final StaticLayout name;
        final List<Entry> entries = new ArrayList<>();
        int height;
        Column(ResultsTable.Player player, int start, int width) {
            this.player = player; name = layout(player.label() + (start == 0 ? "" : "\nContinued"), 32, true, width - 36);
        }
    }
    static final class Band {
        final List<Column> columns = new ArrayList<>();
        int heading = 0, body = 70;
        int height() { return heading + body + TOTAL + 26; }
    }
    static final class Page {
        final List<Band> bands = new ArrayList<>();
        final List<StaticLayout> bogies = new ArrayList<>();
        final int width, contentTop;
        int height;
        Page(int width, int top) { this.width = width; contentTop = top; height = top + FOOTER; }
    }
    final ResultsTable table;
    final PrizeSummary summary;
    final int calls, width, columnWidth, contentTop;
    final TicketPlan plan;
    final StaticLayout heading, details, balance;
    final List<Page> pages = new ArrayList<>();
    ResultsImage(PrizeBook book, int calls) { this(book, calls, null); }
    ResultsImage(PrizeBook book, int calls, TicketPlan plan) {
        if (!book.hasWinners()) throw new IllegalArgumentException("Save a prize result before sharing results.");
        table = new ResultsTable(book, plan); summary = table.summary; this.calls = calls; this.plan = plan;
        int columns = Math.min(3, table.players.size()); columnWidth = columns == 1 ? 640 : 380;
        width = MARGIN * 2 + columns * columnWidth + (columns - 1) * GAP;
        heading = layout("TAMBOLA · GAME RESULTS", 38, true, width - 2 * MARGIN);
        details = layout(calls + " numbers · " + summary.paidPrizes + " paid · " + summary.bogies.size() + " Bogies · " + table.unawarded + " not awarded", 25, false, width - 2 * MARGIN);
        balance = layout(table.balance(), 25, false, width - 2 * MARGIN);
        contentTop = MARGIN + heading.getHeight() + 16 + details.getHeight() + (plan == null ? 0 : 16 + balance.getHeight()) + 118;
        Page page = new Page(width, contentTop); pages.add(page);
        int capacity = MAX_HEIGHT - contentTop - FOOTER;
        for (int first = 0; first < table.players.size(); first += 3) {
            int end = Math.min(first + 3, table.players.size()), maxWins = 0;
            for (int col = first; col < end; col++) maxWins = Math.max(maxWins, table.players.get(col).wins.size());
            int start = 0;
            do {
                Band band = new Band();
                for (int col = first; col < end; col++) {
                    ResultsTable.Player person = table.players.get(col);
                    if (start > 0 && start >= person.wins.size()) continue;
                    Column column = new Column(person, start, columnWidth); band.columns.add(column); band.heading = Math.max(band.heading, column.name.getHeight() + 32);
                }
                int next = start;
                for (; next < maxWins; next++) {
                    List<Entry> candidates = new ArrayList<>(); int body = band.body;
                    for (Column column : band.columns) {
                        Entry entry = next < column.player.wins.size() ? new Entry(column.player.wins.get(next), columnWidth) : null;
                        candidates.add(entry); if (entry != null) body = Math.max(body, column.height + entry.height);
                    }
                    if (band.heading + body + TOTAL + 26 > capacity) break;
                    for (int col = 0; col < candidates.size(); col++) if (candidates.get(col) != null) {
                        Column column = band.columns.get(col); column.entries.add(candidates.get(col)); column.height += candidates.get(col).height;
                    }
                    band.body = body;
                }
                if (next == start && start < maxWins) throw new IllegalArgumentException("A result is too long to fit in a picture.");
                if (page.height + band.height() > MAX_HEIGHT) { page = new Page(width, contentTop); pages.add(page); }
                page.bands.add(band); page.height += band.height(); start = next;
            } while (start < maxWins);
        }
        // Bogies belong outside player winnings because they have no payout.
        for (PrizeSummary.Bogie bogie : summary.bogies) {
            StaticLayout label = layout("BOGIE · " + bogie.prize + " · ₹0\n" + String.join(", ", bogie.names), 26, false, width - 2 * MARGIN - 36);
            if (label.getHeight() + 32 > capacity) throw new IllegalArgumentException("A Bogie result is too long to fit in a picture.");
            if (page.height + label.getHeight() + 32 > MAX_HEIGHT) { page = new Page(width, contentTop); pages.add(page); }
            page.bogies.add(label); page.height += label.getHeight() + 32;
        }
    }
    Bitmap picture(int index) {
        Page page = pages.get(index); Bitmap bitmap = Bitmap.createBitmap(width, page.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Ui.PAPER); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int top = MARGIN; draw(canvas, heading, MARGIN, top); top += heading.getHeight() + 16;
        draw(canvas, details, MARGIN, top); top += details.getHeight();
        if (plan != null) { top += 16; draw(canvas, balance, MARGIN, top); top += balance.getHeight(); }
        top += 20; fill(canvas, paint, MARGIN, top, width - 2 * MARGIN, 76, Ui.GREEN);
        text(canvas, paint, "TOTAL PAID", MARGIN + 18, top + 49, 28, Color.WHITE);
        paint.setTextAlign(Paint.Align.RIGHT); text(canvas, paint, PrizeBook.money(summary.totalPaise), width - MARGIN - 18, top + 51, 36, Color.WHITE); paint.setTextAlign(Paint.Align.LEFT);
        top = page.contentTop;
        for (Band band : page.bands) {
            int col = 0;
            for (Column column : band.columns) {
                int left = MARGIN + col++ * (columnWidth + GAP);
                fill(canvas, paint, left, top, columnWidth, band.heading, Color.rgb(246, 221, 231)); draw(canvas, column.name, left + 18, top + 16);
                fill(canvas, paint, left, top + band.heading, columnWidth, band.body, Color.WHITE);
                int y = top + band.heading;
                if (column.entries.isEmpty()) draw(canvas, layout("No prizes won", 28, false, columnWidth - 36), left + 18, y + 18);
                for (Entry entry : column.entries) {
                    draw(canvas, entry.label, left + 18, y + 14); y += entry.height;
                    fill(canvas, paint, left + 18, y - 1, columnWidth - 36, 1, Color.rgb(230, 226, 221));
                }
                y = top + band.heading + band.body;
                fill(canvas, paint, left, y, columnWidth, TOTAL, Color.rgb(218, 238, 225));
                draw(canvas, layout("TOTAL  " + PrizeBook.money(column.player.totalPaise), 30, true, columnWidth - 36), left + 18, y + 25);
            }
            top += band.height();
        }
        for (StaticLayout bogie : page.bogies) {
            fill(canvas, paint, MARGIN, top, width - 2 * MARGIN, bogie.getHeight() + 24, Color.rgb(246, 221, 231));
            draw(canvas, bogie, MARGIN + 18, top + 12); top += bogie.getHeight() + 32;
        }
        draw(canvas, layout("Totals are for the full game. Shared amounts are per player.", 23, false, width - 2 * MARGIN - 110), MARGIN, page.height - 70);
        paint.setTextAlign(Paint.Align.RIGHT); text(canvas, paint, (index + 1) + " / " + pages.size(), width - MARGIN, page.height - 40, 23, Ui.INK);
        return bitmap;
    }
    private static StaticLayout layout(String text, int size, boolean bold, int width) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(size); paint.setColor(Ui.INK);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width).setIncludePad(false).setLineSpacing(4, 1).build();
    }
    private static void draw(Canvas canvas, StaticLayout text, int x, int y) { canvas.save(); canvas.translate(x, y); text.draw(canvas); canvas.restore(); }
    private static void fill(Canvas canvas, Paint paint, int x, int y, int width, int height, int color) { paint.setColor(color); canvas.drawRect(x, y, x + width, y + height, paint); }
    private static void text(Canvas canvas, Paint paint, String value, int x, int y, int size, int color) {
        paint.setColor(color); paint.setTextSize(size); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); canvas.drawText(value, x, y, paint);
    }
}
