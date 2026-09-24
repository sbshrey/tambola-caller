package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int INK = Color.rgb(41, 38, 56), PINK = Color.rgb(169, 30, 89), GREEN = Color.rgb(17, 121, 78), PAPER = Color.rgb(251, 248, 243);
    static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    static TextView text(Context context, String value, int size) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size); view.setTextColor(INK);
        view.setPadding(0, dp(context, 4), 0, dp(context, 4)); return view;
    }
    static Button button(Context context, String label, int color, Runnable action) {
        Button view = new Button(context); view.setText(label); view.setAllCaps(false); view.setTextSize(17);
        view.setTypeface(null, Typeface.BOLD); view.setTextColor(color == PAPER ? INK : Color.WHITE);
        GradientDrawable background = new GradientDrawable(); background.setColor(color); background.setCornerRadius(dp(context, 12)); view.setBackground(background);
        view.setMinHeight(dp(context, 48)); view.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        view.setOnClickListener(v -> action.run()); return view;
    }
    static void add(LinearLayout parent, android.view.View view, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(parent.getContext(), height));
        params.topMargin = dp(parent.getContext(), 6); parent.addView(view, params);
    }
    static void weighted(LinearLayout parent, android.view.View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1); params.setMargins(dp(parent.getContext(), 3), 0, dp(parent.getContext(), 3), 0); parent.addView(view, params);
    }
}
