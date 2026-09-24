package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.inputmethod.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class Sharing {
    static final String NUMBER = "number", RECENT = "recent", BOARD = "board", AUDIO = "audio";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    static String recent(Game game) { StringBuilder text = new StringBuilder("Recent numbers (latest first)\n"); for (int i = game.count() - 1; i >= Math.max(0, game.count() - 10); i--) text.append(Game.emoji(game.called().get(i))).append(i == Math.max(0, game.count() - 10) ? "" : "  "); return text.toString(); }
    static void prepare(Context context, Game game, String language, String kind, Consumer<Uri> ready, Consumer<String> failed) {
        Context app = context.getApplicationContext();
        worker.execute(() -> { try { Uri uri = create(app, game, language, kind); main.post(() -> ready.accept(uri)); }
            catch (IOException | RuntimeException error) { main.post(() -> failed.accept("Could not prepare the file. Please try again.")); } });
    }
    static Uri create(Context context, Game game, String language, String kind) throws IOException {
        if (game.latest() == 0) throw new IllegalArgumentException("Call a number first.");
        if (!Arrays.asList(NUMBER, RECENT, BOARD, AUDIO).contains(kind)) throw new IllegalArgumentException("Unknown picture.");
        File folder = ShareProvider.folder(context); File[] old = folder.listFiles(); if (old != null) for (File file : old) if (file.lastModified() < System.currentTimeMillis() - 48L * 60 * 60 * 1000) file.delete();
        File file = new File(folder, UUID.randomUUID() + (AUDIO.equals(kind) ? ".mp3" : ".png"));
        try (OutputStream output = new FileOutputStream(file)) {
            if (AUDIO.equals(kind)) try (InputStream input = context.getAssets().open(CallAudio.asset(language, game.latest()))) { byte[] buffer = new byte[8192]; int size; while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size); }
            else { Bitmap picture = picture(context, game, language, kind); try { if (!picture.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("PNG failed"); } finally { picture.recycle(); } }
        } catch (IOException | RuntimeException error) { file.delete(); throw error; }
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".shared").appendPath(file.getName()).build();
    }
    static Bitmap picture(Context context, Game game, String language, String kind) {
        int height = BOARD.equals(kind) ? 1280 : 1000; Bitmap bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Ui.PAPER); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        center(canvas, paint, "TAMBOLA", 540, 90, 44, Ui.PINK);
        center(canvas, paint, game.count() + " of 90 called", 540, 152, 32, Ui.INK);
        if (NUMBER.equals(kind)) {
            center(canvas, paint, "NUMBER CALLED", 540, 290, 40, Ui.INK);
            center(canvas, paint, Integer.toString(game.latest()), 540, 640, 310, Ui.PINK);
            wrap(canvas, paint, CallAudio.phrase(context, language, game.latest()), 540, 770, 38, 940);
        } else if (RECENT.equals(kind)) {
            center(canvas, paint, "RECENT NUMBERS", 540, 260, 44, Ui.INK);
            center(canvas, paint, "Latest first", 540, 320, 30, Ui.INK);
            for (int i = 0; i < Math.min(10, game.count()); i++) {
                int x = 80 + (i % 5) * 188, y = 410 + (i / 5) * 200; paint.setColor(i == 0 ? Ui.PINK : Color.WHITE); canvas.drawRoundRect(x, y, x + 168, y + 164, 20, 20, paint);
                center(canvas, paint, Integer.toString(game.called().get(game.count() - 1 - i)), x + 84, y + 113, 76, i == 0 ? Color.WHITE : Ui.INK);
            }
        } else {
            center(canvas, paint, "Latest number: " + game.latest(), 540, 235, 44, Ui.PINK);
            for (int value = 1; value <= 90; value++) {
                int x = 40 + ((value - 1) % 10) * 100, y = 290 + ((value - 1) / 10) * 92; boolean latest = value == game.latest(), called = game.called().contains(value);
                paint.setColor(latest ? Ui.PINK : called ? Color.rgb(247, 213, 227) : Color.WHITE); canvas.drawRoundRect(x, y, x + 90, y + 82, 9, 9, paint);
                center(canvas, paint, Integer.toString(value), x + 45, y + 56, 38, latest ? Color.WHITE : Ui.INK);
            }
            center(canvas, paint, "Pink = called · Dark pink = latest", 540, 1190, 28, Ui.INK);
        }
        center(canvas, paint, "Mark your paper ticket", 540, height - 35, 28, Ui.INK); return bitmap;
    }
    private static void center(Canvas canvas, Paint paint, String text, float x, float y, float size, int color) { paint.setColor(color); paint.setTextSize(size); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(text, x, y, paint); }
    private static void wrap(Canvas canvas, Paint paint, String text, float x, float y, int size, int width) {
        paint.setTextSize(size); String line = ""; int count = 0;
        for (String word : text.split(" ")) { String next = line.isEmpty() ? word : line + " " + word; if (paint.measureText(next) > width && !line.isEmpty()) { center(canvas, paint, line, x, y + count++ * 54, size, Ui.INK); line = word; } else line = next; }
        center(canvas, paint, line, x, y + count * 54, size, Ui.INK);
    }
    static boolean accepts(EditorInfo info, String mime) { if (info == null || info.contentMimeTypes == null) return false; for (String type : info.contentMimeTypes) if (ClipDescription.compareMimeTypes(mime, type)) return true; return false; }
    static boolean insert(InputConnection editor, EditorInfo info, Uri uri, String mime) {
        if (editor == null || !accepts(info, mime)) return false;
        try { return editor.commitContent(new InputContentInfo(uri, new ClipDescription("Tambola", new String[]{mime}), null), InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null); }
        catch (RuntimeException error) { return false; }
    }
    static void file(Context context, Uri uri, String mime) {
        Intent send = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newUri(context.getContentResolver(), "Tambola", uri));
        chooser(context, send);
    }
    static void text(Context context, String text) { chooser(context, new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)); }
    private static void chooser(Context context, Intent send) { context.startActivity(Intent.createChooser(send, "Choose WhatsApp, then your group").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
}
