package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

final class Preferences {
    static final String[] LANGUAGES = {"en", "hi", "hinglish"};
    static final String[] LABELS = {"English", "हिन्दी · Hindi", "Hinglish"};
    private final SharedPreferences data;
    Preferences(Context context) { data = context.getSharedPreferences("caller-settings", Context.MODE_PRIVATE); }
    String language() { String value = data.getString("language", "en"); for (String item : LANGUAGES) if (item.equals(value)) return value; return "en"; }
    String label() { for (int i = 0; i < LANGUAGES.length; i++) if (LANGUAGES[i].equals(language())) return LABELS[i]; return LABELS[0]; }
    boolean voice() { return data.getBoolean("voice", true); }
    String dictationLanguage() { return data.getString("dictation-language", language().equals("hi") ? "hi-IN" : "en-IN"); }
    void dictationLanguage(String value) { if (!value.equals("en-IN") && !value.equals("hi-IN")) throw new IllegalArgumentException("Choose English or Hindi."); save(data.edit().putString("dictation-language", value)); }
    void language(String value) { if (!java.util.Arrays.asList(LANGUAGES).contains(value)) throw new IllegalArgumentException("Choose a language."); save(data.edit().putString("language", value)); }
    void voice(boolean value) { save(data.edit().putBoolean("voice", value)); }
    private void save(SharedPreferences.Editor edit) { if (!edit.commit()) throw new IllegalStateException("Could not save. Try again."); }
}
