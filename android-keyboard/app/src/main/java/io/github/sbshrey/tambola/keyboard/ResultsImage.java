package io.github.sbshrey.tambola.keyboard;

import android.graphics.*;
import android.text.*;
import java.util.*;

/** A transposed results matrix. Repeat headings and full-game totals on every page. */
final class ResultsImage {
    static final int MAX_HEIGHT = 4096, MAX_PLAYERS = 12, MARGIN = 40, SCHEME = 330, PLAYER = 150, TOTAL = 160, FOOTER = 100;
    static final class Row {
        final ResultsTable.Row result;
        final StaticLayout label;
        final int height;
        Row(ResultsTable.Row result) { this.result = result; label = layout(result.label(), 27, false, SCHEME - 28); height = Math.max(80, label.getHeight() + 28); }
    }
    static final class Page {
        final List<Row> rows = new ArrayList<>();
        final List<StaticLayout> names;
        final int firstPlayer, endPlayer, width, heading, tableTop, bannerTop;
        int height;
        Page(int firstPlayer, int endPlayer, List<StaticLayout> names, int heading, String balance) {
            this.firstPlayer = firstPlayer; this.endPlayer = endPlayer; this.names = names; this.heading = heading;
            width = MARGIN * 2 + SCHEME + (endPlayer - firstPlayer) * PLAYER + TOTAL;
            bannerTop = balance.isEmpty() ? 232 : Math.max(232, 176 + layout(balance, 25, false, width - 2 * MARGIN).getHeight());
            tableTop = bannerTop + 128 + heading; height = tableTop + 100 + FOOTER;
        }
    }
    final ResultsTable table;
    final PrizeSummary summary;
    final int calls;
    final TicketPlan plan;
    final List<Page> pages = new ArrayList<>();
    ResultsImage(PrizeBook book, int calls) { this(book, calls, null); }
    ResultsImage(PrizeBook book, int calls, TicketPlan plan) {
        if (!book.hasWinners()) throw new IllegalArgumentException("Save a prize result before sharing results.");
        table = new ResultsTable(book, plan); summary = table.summary; this.calls = calls; this.plan = plan;
        for (int first = 0; first < table.players.size(); first += MAX_PLAYERS) {
            int end = Math.min(first + MAX_PLAYERS, table.players.size()), heading = 100;
            List<StaticLayout> names = new ArrayList<>();
            for (int col = first; col < end; col++) { StaticLayout name = layout(table.players.get(col).label(), 27, true, PLAYER - 24); names.add(name); heading = Math.max(heading, name.getHeight() + 32); }
            Page page = new Page(first, end, names, heading, table.balance()); pages.add(page);
            for (ResultsTable.Row result : table.rows) {
                Row row = new Row(result);
                if (page.height + row.height > MAX_HEIGHT) { page = new Page(first, end, names, heading, table.balance()); pages.add(page); }
                page.rows.add(row); page.height += row.height;
            }
        }
    }
    Bitmap picture(int index) {
        Page page = pages.get(index); Bitmap bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Ui.PAPER); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        text(canvas, paint, "TAMBOLA · GAME RESULTS", MARGIN, 65, 38, Ui.PINK);
        draw(canvas, layout(calls + " numbers · " + summary.paidPrizes + " paid · " + summary.bogies.size() + " Bogies · " + table.unawarded + " not awarded", 24, false, page.width - 2 * MARGIN), MARGIN, 91);
        if (plan != null) draw(canvas, layout(table.balance(), 25, false, page.width - 2 * MARGIN), MARGIN, 160);
        paint.setColor(Ui.GREEN); canvas.drawRoundRect(MARGIN, page.bannerTop, page.width - MARGIN, page.bannerTop + 78, 15, 15, paint);
        text(canvas, paint, "TOTAL PAID", MARGIN + 20, page.bannerTop + 51, 28, Color.WHITE);
        paint.setTextAlign(Paint.Align.RIGHT); text(canvas, paint, PrizeBook.money(summary.totalPaise), page.width - MARGIN - 20, page.bannerTop + 54, 38, Color.WHITE); paint.setTextAlign(Paint.Align.LEFT);
        text(canvas, paint, "Players " + (page.firstPlayer + 1) + "–" + page.endPlayer + " / " + table.players.size() + " · totals for full game", MARGIN, page.bannerTop + 112, 23, Ui.INK);
        paint.setColor(Color.rgb(236, 230, 224)); canvas.drawRect(MARGIN, page.bannerTop + 128, page.width - MARGIN, page.tableTop, paint);
        draw(canvas, layout("SCHEME / RESULT", 25, true, SCHEME - 24), MARGIN + 14, page.bannerTop + 144);
        for (int col = page.firstPlayer; col < page.endPlayer; col++) draw(canvas, page.names.get(col - page.firstPlayer), MARGIN + SCHEME + (col - page.firstPlayer) * PLAYER + 12, page.bannerTop + 144);
        draw(canvas, layout("PAID\nAll players", 25, true, TOTAL - 24), page.width - MARGIN - TOTAL + 12, page.bannerTop + 144);
        int top = page.tableTop, rowIndex = 0;
        for (Row row : page.rows) {
            paint.setColor(row.result.bogie ? Color.rgb(247, 228, 234) : rowIndex++ % 2 == 0 ? Color.WHITE : Color.rgb(242, 240, 235));
            canvas.drawRect(MARGIN, top, page.width - MARGIN, top + row.height, paint);
            draw(canvas, row.label, MARGIN + 14, top + 14);
            for (int col = page.firstPlayer; col < page.endPlayer; col++) center(canvas, paint, row.result.share(col), MARGIN + SCHEME + (col - page.firstPlayer) * PLAYER + PLAYER / 2, top + row.height / 2 + 10, 29, row.result.shares.get(col) > 0 ? Ui.GREEN : Color.GRAY);
            center(canvas, paint, PrizeBook.money(row.result.totalPaise), page.width - MARGIN - TOTAL / 2, top + row.height / 2 + 10, 29, Ui.INK);
            top += row.height;
        }
        paint.setColor(Color.rgb(218, 238, 225)); canvas.drawRect(MARGIN, top, page.width - MARGIN, top + 100, paint);
        draw(canvas, layout("TOTAL\nFull game", 27, true, SCHEME - 24), MARGIN + 14, top + 16);
        for (int col = page.firstPlayer; col < page.endPlayer; col++) center(canvas, paint, PrizeBook.money(table.players.get(col).totalPaise), MARGIN + SCHEME + (col - page.firstPlayer) * PLAYER + PLAYER / 2, top + 60, 29, Ui.GREEN);
        center(canvas, paint, PrizeBook.money(summary.totalPaise), page.width - MARGIN - TOTAL / 2, top + 60, 29, Ui.GREEN);
        paint.setColor(Color.rgb(211, 208, 202)); paint.setStrokeWidth(1);
        for (int col = 0; col <= page.endPlayer - page.firstPlayer; col++) { int x = MARGIN + SCHEME + col * PLAYER; canvas.drawLine(x, page.bannerTop + 128, x, top + 100, paint); }
        draw(canvas, layout("Each cell is that player's share. — = no payout. Bogies pay ₹0.", 23, false, page.width - 2 * MARGIN - 130), MARGIN, page.height - 72);
        paint.setTextAlign(Paint.Align.RIGHT); text(canvas, paint, (index + 1) + " / " + pages.size(), page.width - MARGIN, page.height - 42, 23, Ui.INK);
        return bitmap;
    }
    private static StaticLayout layout(String text, int size, boolean bold, int width) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(size); paint.setColor(Ui.INK);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width).setIncludePad(false).setLineSpacing(4, 1).build();
    }
    private static void draw(Canvas canvas, StaticLayout text, int x, int y) { canvas.save(); canvas.translate(x, y); text.draw(canvas); canvas.restore(); }
    private static void center(Canvas canvas, Paint paint, String value, int x, int y, int size, int color) { paint.setTextSize(size); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        int fitted = Math.min(size, (int) (size * (PLAYER - 20) / Math.max(1, paint.measureText(value))));
        paint.setTextAlign(Paint.Align.CENTER); text(canvas, paint, value, x, y, fitted, color); paint.setTextAlign(Paint.Align.LEFT); }
    private static void text(Canvas canvas, Paint paint, String value, int x, int y, int size, int color) {
        paint.setColor(color); paint.setTextSize(size); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); canvas.drawText(value, x, y, paint);
    }
}
