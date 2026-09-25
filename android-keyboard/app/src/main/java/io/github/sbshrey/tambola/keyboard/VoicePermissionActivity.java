package io.github.sbshrey.tambola.keyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.WindowInsets;
import android.widget.*;

/** Permission setup only. Speech starts from the chat after the host returns. */
public final class VoicePermissionActivity extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); render(); }
    @Override public void onResume() { super.onResume(); render(); }
    private void render() {
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this, 24); page.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Ui.PAPER); scroll.addView(page); setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener((view, insets) -> { android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets; });
        Ui.add(page, Ui.text(this, "Speak prize + winner", 28), -2);
        boolean allowed = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        Ui.add(page, Ui.text(this, allowed ? "Microphone is ready. Return to your chat, tap Winners, then Speak prize + winner."
            : "Allow the microphone to speak a winner. Your phone's speech service turns speech into text and may use the Internet. Recording starts only when you tap Speak in the keyboard.", 21), -2);
        Ui.add(page, Ui.text(this, "Example: Early five winner Asha Sharma.\nThe winner is saved and the announcement goes into the message box. Check the name, then tap WhatsApp Send.", 20), -2);
        if (!allowed) {
            Ui.action(page, "Allow microphone", Ui.GREEN, () -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1));
            Ui.action(page, "App permission settings", Ui.PAPER, () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        }
        Ui.action(page, "Return to chat", Ui.PINK, this::finish);
        Ui.add(page, Ui.text(this, "You can always choose winners manually without microphone permission.", 18), -2);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) { super.onRequestPermissionsResult(code, permissions, results); render(); }
}
