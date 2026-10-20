package io.github.sbshrey.tambola.keyboard;

import android.graphics.*;
import android.text.*;
import java.util.*;

/** Remaining schemes (including open Bogies) first, then winners. Headings repeat on new pages. */
final class SchemeStatusImage {
    static final int WIDTH = 1200, MAX_HEIGHT = 4096, MARGIN = 32, GAP = 16, FOOTER = 100, SECTION = 64;
    static final int FULL = WIDTH - MARGIN * 2, COLUMN = (FULL - GAP) / 2;
    static final class Card {
        final SchemeStatus.Entry entry;
        final StaticLayout title, detail;
        final int width, height;
        Card(SchemeStatus.Entry entry) { this(entry, entry.detail, false); }
        Card(SchemeStatus.Entry entry, String text, boolean continued) {
            this.entry = entry; width = entry.state == SchemeStatus.State.BOGIE ? FULL : COLUMN;
            title = layout(entry.name + (continued ? " · continued" : ""), 32, true, width - 40); detail = layout(text, 28, false, width - 40);
            height = title.getHeight() + detail.getHeight() + 48;
        }
    }
    static final class Row {
        final String heading;
        final List<Card> cards;
        final int height;
        Row(String heading, List<Card> cards) {
            this.heading = heading; this.cards = Collections.unmodifiableList(cards);
            int body = 0; for (Card card : cards) body = Math.max(body, card.height);
            height = (heading == null ? 0 : SECTION) + body + GAP;
        }
    }
    static final class Page {
        final List<Row> rows = new ArrayList<>();
        int height;
        Page(int top) { height = top + FOOTER; }
    }
    final SchemeStatus status;
    final List<Page> pages = new ArrayList<>();
    final StaticLayout heading, progress, counts;
    final int contentTop;
    SchemeStatusImage(PrizeBook book, Game game, TicketPlan plan) {
        status = new SchemeStatus(book, plan);
        heading = layout("TAMBOLA · SCHEME STATUS", 40, true, FULL);
        progress = layout(game.count() == 0 ? "Before the first call" : "After " + game.count() + " calls · Latest number: " + game.latest(), 28, false, FULL);
        counts = layout(status.selected + " selected · " + status.remaining.size() + " remaining"
            + (status.bogies.isEmpty() ? "" : " (" + status.bogies.size() + " Bogie)") + " · " + status.won.size() + " won", 28, false, FULL - 40);
        contentTop = MARGIN + heading.getHeight() + 16 + progress.getHeight() + 20 + counts.getHeight() + 40 + 16;
        pages.add(new Page(contentTop));
        append("REMAINING · " + status.remaining.size(), status.remaining);
        append("WON · " + status.won.size() + " · Paid " + PrizeBook.money(status.paidPaise), status.won);
    }
    private void append(String heading, List<SchemeStatus.Entry> entries) {
        List<Card> all = new ArrayList<>();
        for (SchemeStatus.Entry entry : entries) {
            String remaining = entry.detail; boolean continued = false;
            while (!remaining.isEmpty()) {
                Card card = new Card(entry, remaining, continued);
                int available = MAX_HEIGHT - contentTop - FOOTER - SECTION - GAP - card.title.getHeight() - 48;
                if (card.detail.getHeight() <= available) { all.add(card); break; }
                int lines = 0;
                while (lines < card.detail.getLineCount() && card.detail.getLineBottom(lines) <= available) lines++;
                if (lines == 0) throw new IllegalArgumentException("A scheme result is too long to fit in a picture.");
                int end = card.detail.getLineEnd(lines - 1); all.add(new Card(entry, remaining.substring(0, end), continued));
                remaining = remaining.substring(end); continued = true;
            }
        }
        boolean first = true;
        for (int i = 0; i < all.size();) {
            List<Card> cards = new ArrayList<>(); Card card = all.get(i++); cards.add(card);
            if (card.width == COLUMN && i < all.size() && all.get(i).width == COLUMN) cards.add(all.get(i++));
            Page page = pages.get(pages.size() - 1); Row row = new Row(first ? heading : null, cards);
            if (page.height + row.height > MAX_HEIGHT) {
                page = new Page(contentTop); pages.add(page); row = new Row(heading + (first ? "" : " · continued"), cards);
            }
            if (page.height + row.height > MAX_HEIGHT) throw new IllegalArgumentException("A scheme result is too long to fit in a picture.");
            page.rows.add(row); page.height += row.height; first = false;
        }
    }

    Bitmap picture(int index) {
        Page page = pages.get(index); Bitmap bitmap = Bitmap.createBitmap(WIDTH, page.height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Ui.PAPER); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int top = MARGIN; draw(canvas, heading, MARGIN, top); top += heading.getHeight() + 16;
        draw(canvas, progress, MARGIN, top); top += progress.getHeight() + 20;
        fill(canvas, paint, MARGIN, top, FULL, counts.getHeight() + 40, Color.rgb(218, 238, 225)); draw(canvas, counts, MARGIN + 20, top + 20);
        top = contentTop;
        for (Row row : page.rows) {
            if (row.heading != null) { draw(canvas, layout(row.heading, 28, true, FULL), MARGIN, top + 17); top += SECTION; }
            int left = MARGIN, body = row.height - (row.heading == null ? 0 : SECTION) - GAP;
            for (Card card : row.cards) {
                int color = card.entry.state == SchemeStatus.State.REMAINING ? Color.rgb(255, 244, 213) : card.entry.state == SchemeStatus.State.WON ? Color.WHITE : Color.rgb(246, 221, 231);
                fill(canvas, paint, left, top, card.width, body, color); draw(canvas, card.title, left + 20, top + 18);
                draw(canvas, card.detail, left + 20, top + 30 + card.title.getHeight()); left += card.width + GAP;
            }
            top += body + GAP;
        }
        draw(canvas, layout("Bogie stays open. Only saved winners close schemes.\nShare an updated picture after the next result.", 23, false, FULL - 120), MARGIN, page.height - 78);
        draw(canvas, layout((index + 1) + " / " + pages.size(), 24, true, 100), WIDTH - MARGIN - 100, page.height - 48);
        return bitmap;
    }
    private static StaticLayout layout(String text, int size, boolean bold, int width) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(size); paint.setColor(Ui.INK);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width).setIncludePad(false).setLineSpacing(4, 1).build();
    }
    private static void draw(Canvas canvas, StaticLayout text, int x, int y) { canvas.save(); canvas.translate(x, y); text.draw(canvas); canvas.restore(); }
    private static void fill(Canvas canvas, Paint paint, int x, int y, int width, int height, int color) { paint.setColor(color); canvas.drawRect(x, y, x + width, y + height, paint); }
}
