package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Same-UID fixture configuration, excluded from release. Recognition runs in a separate process. */
public final class TestSpeechProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle config = new Bundle(); config.putString("transcript", TestRecognitionService.transcript);
        config.putInt("failure", TestRecognitionService.failure); config.putBoolean("wait", TestRecognitionService.waitForResult); return config;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
