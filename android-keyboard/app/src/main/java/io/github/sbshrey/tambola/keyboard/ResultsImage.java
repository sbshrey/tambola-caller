package io.github.sbshrey.tambola.keyboard;

import android.graphics.*;
import android.text.*;
import java.util.*;

/** Readable PNG tables, paginated only when a large result cannot fit in one image. */
final class ResultsImage {
    static final int WIDTH = 1080, MAX_HEIGHT = 4096, HEADER = 320, FOOTER = 80;
    static final class Row {
        final StaticLayout name, prizes, total;
        final boolean bogie;
        final int height;
        Row(String name, String prizes, String total, boolean bogie) {
            this.name = layout(name, 30, true, 240); this.prizes = layout(prizes, 27, false, 530);
            this.total = layout(total, 30, true, 170); this.bogie = bogie;
            height = Math.max(88, Math.max(this.name.getHeight(), Math.max(this.prizes.getHeight(), this.total.getHeight())) + 36);
        }
    }
    static final class Page {
        final List<Row> rows = new ArrayList<>();
        int height;
        Page(int header) { height = header + FOOTER; }
    }
    final PrizeSummary summary;
    final int calls;
    final TicketPlan plan;
    final int header;
    final List<Page> pages = new ArrayList<>();
    ResultsImage(PrizeBook book, int calls) { this(book, calls, null); }
    ResultsImage(PrizeBook book, int calls, TicketPlan plan) {
        if (!book.hasWinners()) throw new IllegalArgumentException("Save a prize result before sharing results.");
        if (plan != null) plan.validateLive(book);
        summary = new PrizeSummary(book); this.calls = calls; this.plan = plan; header = plan == null ? HEADER : HEADER + 44;
        for (PrizeSummary.Player player : summary.players) {
            List<String> prizes = player.prizes.isEmpty() ? Collections.singletonList("No prizes won") : player.prizes;
            for (int start = 0; start < prizes.size(); start += 10) {
                int sets = 0; if (plan != null) for (Map.Entry<String, String> person : book.players.entrySet()) if (person.getValue().equals(player.name)) sets = plan.sets(person.getKey());
                String label = player.name + (start == 0 ? "" : " (continued)") + (plan == null ? "" : "\n" + sets + (sets == 1 ? " set" : " sets"));
                add(new Row(label, String.join("\n", prizes.subList(start, Math.min(start + 10, prizes.size()))),
                    start == 0 ? PrizeBook.money(player.totalPaise) : "—", false));
            }
        }
        for (PrizeSummary.Bogie bogie : summary.bogies) {
            add(new Row("Bogie\nNo payout", bogie.prize + "\n" + bogie.names.size() + " claimants", "₹0", true));
        }
    }
    private void add(Row row) {
        if (row.height + header + FOOTER > MAX_HEIGHT) throw new IllegalArgumentException("Result text is too large for a picture.");
        if (pages.isEmpty() || pages.get(pages.size() - 1).height + row.height > MAX_HEIGHT) pages.add(new Page(header));
        Page page = pages.get(pages.size() - 1); page.rows.add(row); page.height += row.height;
    }
    Bitmap picture(int index) {
        Page page = pages.get(index); Bitmap bitmap = Bitmap.createBitmap(WIDTH, page.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Ui.PAPER); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        text(canvas, paint, "TAMBOLA · GAME RESULTS", 48, 70, 42, Ui.PINK);
        text(canvas, paint, calls + " numbers called · " + summary.paidPrizes + (summary.paidPrizes == 1 ? " prize awarded · " : " prizes awarded · ")
            + summary.bogies.size() + (summary.bogies.size() == 1 ? " Bogie" : " Bogies"), 48, 122, 27, Ui.INK);
        int offset = header - HEADER;
        if (plan != null) text(canvas, paint, plan.totalSets() + " sets · Collected " + PrizeBook.money(plan.collection() * 100) + " · Unpaid "
            + PrizeBook.money(plan.collection() * 100 - summary.totalPaise), 48, 163, 27, Ui.INK);
        paint.setColor(Ui.GREEN); canvas.drawRoundRect(40, 151 + offset, 1040, 244 + offset, 18, 18, paint);
        text(canvas, paint, "TOTAL PRIZES", 64, 210 + offset, 30, Color.WHITE);
        paint.setTextAlign(Paint.Align.RIGHT); text(canvas, paint, PrizeBook.money(summary.totalPaise), 1012, 214 + offset, 42, Color.WHITE); paint.setTextAlign(Paint.Align.LEFT);
        text(canvas, paint, "PLAYER", 56, 294 + offset, 24, Ui.INK); text(canvas, paint, "PRIZES WON / EACH SHARE", 310, 294 + offset, 24, Ui.INK); text(canvas, paint, "TOTAL", 862, 294 + offset, 24, Ui.INK);
        int top = header, rowIndex = 0;
        for (Row row : page.rows) {
            paint.setColor(row.bogie ? Color.rgb(247, 228, 234) : rowIndex++ % 2 == 0 ? Color.WHITE : Color.rgb(242, 240, 235));
            canvas.drawRect(40, top, 1040, top + row.height, paint);
            draw(canvas, row.name, 56, top + 18); draw(canvas, row.prizes, 310, top + 18); draw(canvas, row.total, 862, top + 18);
            top += row.height;
        }
        text(canvas, paint, "Shared prizes show each player's share · Bogies pay ₹0", 48, page.height - 40, 23, Ui.INK);
        paint.setTextAlign(Paint.Align.RIGHT); text(canvas, paint, (index + 1) + " / " + pages.size(), 1032, page.height - 40, 23, Ui.INK);
        return bitmap;
    }
    private static StaticLayout layout(String text, int size, boolean bold, int width) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(size); paint.setColor(Ui.INK);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width).setIncludePad(false).setLineSpacing(5, 1).build();
    }
    private static void draw(Canvas canvas, StaticLayout text, int x, int y) { canvas.save(); canvas.translate(x, y); text.draw(canvas); canvas.restore(); }
    private static void text(Canvas canvas, Paint paint, String value, int x, int y, int size, int color) {
        paint.setColor(color); paint.setTextSize(size); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); canvas.drawText(value, x, y, paint);
    }
}
