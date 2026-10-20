package io.github.sbshrey.tambola.keyboard;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Local editor fixture for testing a trip from the keyboard to setup and back. */
public final class KeyboardEditorActivity extends Activity {
    EditText draft;
    boolean rejectCommit;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout page = Ui.column(this);
        Ui.add(page, Ui.text(this, "Test chat editor", 22), -2);
        draft = new EditText(this) {
            @Override public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info) {
                android.view.inputmethod.InputConnection base = super.onCreateInputConnection(info);
                return base == null ? null : new android.view.inputmethod.InputConnectionWrapper(base, false) {
                    @Override public boolean commitText(CharSequence text, int position) {
                        return !rejectCommit && super.commitText(text, position);
                    }
                };
            }
        }; draft.setTextSize(24); draft.setContentDescription("Test chat draft");
        draft.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Ui.add(page, draft, 72); setContentView(page);
    }
}
