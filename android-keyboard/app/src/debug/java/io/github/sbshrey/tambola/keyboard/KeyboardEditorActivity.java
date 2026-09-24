package io.github.sbshrey.tambola.keyboard;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Local editor fixture for testing a trip from the keyboard to setup and back. */
public final class KeyboardEditorActivity extends Activity {
    EditText draft;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout page = Ui.column(this);
        Ui.add(page, Ui.text(this, "Test chat editor", 22), -2);
        draft = new EditText(this); draft.setTextSize(24); draft.setContentDescription("Test chat draft");
        draft.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Ui.add(page, draft, 72); setContentView(page);
    }
}
