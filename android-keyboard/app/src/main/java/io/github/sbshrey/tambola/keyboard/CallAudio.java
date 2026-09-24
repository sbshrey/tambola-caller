package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.content.Intent;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

final class CallAudio {
    private final Context context;
    private int generation;
    private static final Map<String, JSONObject> catalogs = new HashMap<>();
    CallAudio(Context context) { this.context = context.getApplicationContext(); }
    static String asset(String language, int number) {
        if (!Arrays.asList(Preferences.LANGUAGES).contains(language) || number < 1 || number > 90) throw new IllegalArgumentException("Choose a called number.");
        return String.format(Locale.ROOT, "voices/%s/numbers/%02d.mp3", language, number);
    }
    static synchronized String phrase(Context context, String language, int number) {
        if (number == 0) return "Ready for your first number";
        try {
            if (!catalogs.containsKey(language)) try (InputStream input = context.getAssets().open("voices/" + language + "/manifest.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int length; while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
                catalogs.put(language, new JSONObject(bytes.toString("UTF-8")).getJSONObject("clips"));
            }
            return catalogs.get(language).getJSONObject(Integer.toString(number)).getString("phrase");
        } catch (Exception error) { return "Number " + number; }
    }
    void play(int number, String language, Consumer<String> feedback) {
        stop(); if (number == 0) return;
        int started = generation;
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) { @Override protected void onReceiveResult(int code, Bundle data) { if (code == 1 && started == generation) feedback.accept("Could not play sound. Try Hear again. Your number is saved."); } };
        try { context.startForegroundService(new Intent(context, CallPlaybackService.class).putExtra("number", number).putExtra("language", language).putExtra("result", receiver)); }
        catch (RuntimeException error) { feedback.accept("Could not play sound. Try Hear again. Your number is saved."); }
    }
    void stop() { generation++; context.stopService(new Intent(context, CallPlaybackService.class)); }
}
