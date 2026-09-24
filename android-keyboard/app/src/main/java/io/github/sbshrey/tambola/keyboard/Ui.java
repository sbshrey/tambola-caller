package io.github.sbshrey.tambola.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
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
        Button view = new Button(context); view.setText(label); view.setAllCaps(false); view.setTextSize(18);
        view.setTypeface(null, Typeface.BOLD); view.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{Color.DKGRAY, color == PAPER ? INK : Color.WHITE}));
        StateListDrawable backgrounds = new StateListDrawable();
        backgrounds.addState(new int[]{-android.R.attr.state_enabled}, background(context, Color.rgb(225, 223, 218)));
        backgrounds.addState(new int[]{android.R.attr.state_pressed}, background(context, color == PAPER ? Color.LTGRAY : INK));
        backgrounds.addState(new int[]{}, background(context, color)); view.setBackground(backgrounds);
        view.setMinHeight(dp(context, 52)); view.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        view.setOnClickListener(v -> action.run()); return view;
    }
    private static GradientDrawable background(Context context, int color) { GradientDrawable value = new GradientDrawable(); value.setColor(color); value.setCornerRadius(dp(context, 12)); return value; }
    static void add(LinearLayout parent, android.view.View view, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(parent.getContext(), Math.round(height * Math.max(1, parent.getResources().getConfiguration().fontScale))));
        params.topMargin = dp(parent.getContext(), 6); parent.addView(view, params);
    }
    static void weighted(LinearLayout parent, android.view.View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1); params.setMargins(dp(parent.getContext(), 3), 0, dp(parent.getContext(), 3), 0); parent.addView(view, params);
    }
    static Button action(LinearLayout parent, String label, int color, Runnable action) { Button view = button(parent.getContext(), label, color, action); add(parent, view, -2); return view; }
    static void board(LinearLayout parent, Game game) {
        Context context = parent.getContext();
        for (int row = 0; row < 9; row++) {
            LinearLayout line = new LinearLayout(context);
            for (int col = 0; col < 10; col++) {
                int number = row * 10 + col + 1; boolean latest = number == game.latest(), called = game.called().contains(number);
                TextView cell = text(context, Integer.toString(number), 14); cell.setGravity(Gravity.CENTER);
                cell.setBackgroundColor(latest ? PINK : called ? Color.rgb(247, 213, 227) : Color.WHITE); cell.setTextColor(latest ? Color.WHITE : INK);
                cell.setContentDescription(number + (latest ? ", latest" : called ? ", called" : ", waiting"));
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(context, Math.round(34 * Math.max(1, context.getResources().getConfiguration().fontScale))), 1); size.setMargins(1, 1, 1, 1); line.addView(cell, size);
            }
            add(parent, line, -2);
        }
    }
}
