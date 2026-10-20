package io.github.sbshrey.tambola.keyboard;

import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;

final class GameMessageViews {
    static void show(LinearLayout page, GameStore store, GameMessages.Kind kind, Consumer<GameMessages.Kind> choose,
                     Runnable setup, boolean keyboard, Consumer<String> share, Consumer<String> feedback) {
        Ui.add(page, Ui.text(page.getContext(), "Game messages", 25), -2);
        for (GameMessages.Kind option : GameMessages.Kind.values())
            Ui.action(page, option.label + (kind == option ? " ✓" : ""), kind == option ? Ui.PINK : Ui.PAPER, () -> choose.accept(option))
                .setContentDescription("Game message: " + option.label);
        String preview;
        try { preview = current(store, kind); }
        catch (IllegalArgumentException | IllegalStateException error) {
            Ui.add(page, Ui.text(page.getContext(), error.getMessage(), 19), -2);
            Ui.action(page, "Review tickets & schemes", Ui.GREEN, setup); return;
        }
        if (store.load().count() == 0 && !store.prizes().hasWinners())
            Ui.action(page, "Change players & schemes", Ui.PAPER, setup);
        Ui.action(page, (keyboard ? "Insert " : "Share ") + kind.action, Ui.GREEN, () -> {
            try {
                String latest = current(store, kind);
                if (!latest.equals(preview)) { choose.accept(kind); feedback.accept("Setup changed. Review the updated message, then try again."); return; }
                share.accept(latest);
            } catch (IllegalArgumentException | IllegalStateException error) { choose.accept(kind); feedback.accept(error.getMessage()); }
        });
        Ui.add(page, Ui.text(page.getContext(), keyboard ? "Check the preview below. Insert fills an empty message box; tap WhatsApp Send when ready."
            : "Check the preview below, then choose your group and tap Send.", 18), -2);
        if (kind == GameMessages.Kind.START) Ui.add(page, Ui.text(page.getContext(), "This message does not call a number. Return to Call → Next number when everyone is ready.", 18), -2);
        TextView text = Ui.text(page.getContext(), preview, 20); text.setContentDescription("Game message preview"); Ui.add(page, text, -2);
    }
    private static String current(GameStore store, GameMessages.Kind kind) { return GameMessages.text(kind, store.prizes(), store.plan(), store.load()); }
}
