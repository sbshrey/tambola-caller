package io.github.sbshrey.tambola.keyboard;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only grants read access to individually shared, randomly named cache files. */
public final class ShareProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    static File folder(Context context) { File folder = new File(context.getCacheDir(), "shared"); if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Could not prepare sharing."); return folder; }
    private File file(Uri uri) throws FileNotFoundException {
        if (!uri.getAuthority().equals(getContext().getPackageName() + ".shared") || uri.getPathSegments().size() != 1) throw new FileNotFoundException();
        String name = uri.getLastPathSegment(); if (name == null || !name.matches("[a-f0-9-]{36}\\.(png|mp3)")) throw new FileNotFoundException();
        File file = new File(folder(getContext()), name); if (!file.isFile()) throw new FileNotFoundException(); return file;
    }
    @Override public String getType(Uri uri) { return uri.getPath() != null && uri.getPath().endsWith(".mp3") ? "audio/mpeg" : "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException { if (!"r".equals(mode)) throw new FileNotFoundException("Read only"); return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY); }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = file(uri); String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns); Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "Tambola-" + file.getName(); else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
            cursor.addRow(row); return cursor;
        } catch (FileNotFoundException error) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
