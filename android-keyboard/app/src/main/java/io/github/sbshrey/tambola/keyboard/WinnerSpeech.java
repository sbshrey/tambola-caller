package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.speech.*;
import java.util.ArrayList;
import java.util.function.Consumer;

/** One user-started recognition request. Never retries or records in the background. */
final class WinnerSpeech implements RecognitionListener {
    private final SpeechRecognizer recognizer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Consumer<String> result, error, status;
    private final Runnable timeout = () -> fail("Listening timed out. Tap Speak again.");
    private boolean closed;
    WinnerSpeech(Context context, Consumer<String> result, Consumer<String> error, Consumer<String> status) {
        this.result = result; this.error = error; this.status = status;
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(this);
    }
    void start(String language) {
        Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        handler.postDelayed(timeout, 20000);
        try { recognizer.startListening(request); } catch (RuntimeException problem) { fail("Voice typing could not start. Check your phone's speech service or choose winners manually."); }
    }
    void stop() { if (!closed) { status.accept("Finishing… please wait"); try { recognizer.stopListening(); } catch (RuntimeException problem) { fail("Voice typing stopped. Tap Speak again."); } } }
    void cancel() { if (closed) return; closed = true; handler.removeCallbacks(timeout); recognizer.cancel(); recognizer.destroy(); }
    private void fail(String message) { if (closed) return; cancel(); error.accept(message); }
    @Override public void onReadyForSpeech(Bundle params) { if (!closed) status.accept("Listening… say prize and winner"); }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rms) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { if (!closed) status.accept("Finishing… please wait"); }
    @Override public void onError(int code) {
        fail(code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "Microphone permission is needed. Tap Speak again to set it up."
            : code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? "Did not hear that clearly. Tap Speak again."
            : code == SpeechRecognizer.ERROR_NETWORK || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ? "Check the Internet connection, then tap Speak again."
            : "Voice typing unavailable (" + code + "). Try again or choose winners manually.");
    }
    @Override public void onResults(Bundle results) {
        if (closed) return;
        ArrayList<String> choices = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (choices == null || choices.isEmpty()) { fail("Did not hear a name. Tap Speak again."); return; }
        String spoken = choices.get(0); cancel(); result.accept(spoken);
    }
    @Override public void onPartialResults(Bundle partial) { }
    @Override public void onEvent(int type, Bundle params) { }
}
