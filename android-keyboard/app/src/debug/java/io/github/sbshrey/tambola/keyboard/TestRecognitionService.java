package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.*;
import java.util.ArrayList;
import java.util.Collections;

/** Emulator-only speech provider: deterministic text, no microphone or network access. */
public final class TestRecognitionService extends RecognitionService {
    private Callback pending;
    private Bundle config;
    static String transcript = "Early five winner Asha Sharma";
    static int failure;
    static boolean waitForResult;
    private final BroadcastReceiver completion = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { deliver(); } };
    private static final String COMPLETE = "io.github.sbshrey.tambola.keyboard.TEST_SPEECH_COMPLETE";
    // API 26-32 fixture only; the broadcast completes synthetic speech and is never shipped.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override public void onCreate() {
        super.onCreate();
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(completion, new IntentFilter(COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(completion, new IntentFilter(COMPLETE));
    }
    @Override protected void onStartListening(Intent request, Callback callback) {
        pending = callback;
        config = getContentResolver().call(Uri.parse("content://" + getPackageName() + ".speechtest"), "config", null, null);
        try { callback.readyForSpeech(new Bundle()); } catch (RemoteException error) { throw new IllegalStateException(error); }
        if (!config.getBoolean("wait")) deliver();
    }
    static void complete(Context context) { context.sendBroadcast(new Intent(COMPLETE).setPackage(context.getPackageName())); }
    private void deliver() {
        Callback callback = pending; if (callback == null) return; pending = null;
        try {
            if (config.getInt("failure") != 0) callback.error(config.getInt("failure"));
            else { Bundle result = new Bundle(); result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(Collections.singletonList(config.getString("transcript")))); callback.results(result); }
        } catch (RemoteException error) { throw new IllegalStateException(error); }
    }
    @Override protected void onCancel(Callback callback) { pending = null; }
    @Override protected void onStopListening(Callback callback) { deliver(); }
    @Override public void onDestroy() { unregisterReceiver(completion); super.onDestroy(); }
}
