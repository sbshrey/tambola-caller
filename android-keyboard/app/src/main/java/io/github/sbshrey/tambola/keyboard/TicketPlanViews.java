package io.github.sbshrey.tambola.keyboard;

import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

final class TicketPlanViews {
    private static void safe(Runnable action, Consumer<String> feedback) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException error) { feedback.accept(error.getMessage()); } }
    static void totals(LinearLayout page, TicketPlan plan, PrizeBook book) {
        Ui.add(page, Ui.text(page.getContext(), plan.players() + " players · " + plan.totalSets() + " sets · " + plan.totalSets() * TicketPlan.TICKETS + " tickets", 21), -2);
        Ui.add(page, Ui.text(page.getContext(), "Collected: " + PrizeBook.money(plan.collection() * 100) + "\nPlanned prizes: " + PrizeBook.money(TicketPlan.budget(book) * 100), 22), -2);
        String problem = plan.problem(book); Ui.add(page, Ui.text(page.getContext(), problem == null ? "✓ Prize budget matches ticket sales" : problem, 18), -2);
    }
    static void tickets(LinearLayout page, GameStore store, Runnable refresh, Runnable next, Consumer<String> feedback) {
        PrizeBook book = store.prizes(); TicketPlan plan = store.plan();
        Ui.add(page, Ui.text(page.getContext(), "1. Purchased ticket sets", 25), -2);
        Ui.add(page, Ui.text(page.getContext(), "1 set = 6 tickets = ₹50. Use 0 for someone not playing. Every participant can win any selected scheme.", 19), -2);
        totals(page, plan, book);
        Ui.action(page, "Choose schemes & amounts →", Ui.GREEN, next);
        for (Map.Entry<String, String> player : book.players.entrySet()) {
            int count = plan.sets(player.getKey());
            Ui.add(page, Ui.text(page.getContext(), player.getValue(), 23), -2);
            Ui.add(page, Ui.text(page.getContext(), count == 0 ? "Not playing · ₹0" : count + (count == 1 ? " set · " : " sets · ") + count * 6 + " tickets · " + PrizeBook.money(count * 5000), 19), -2);
            LinearLayout row = new LinearLayout(page.getContext());
            Button minus = Ui.button(page.getContext(), "− 1 set", Ui.PAPER, () -> safe(() -> { store.plan(plan.with(player.getKey(), count - 1), book, false); refresh.run(); }, feedback));
            minus.setContentDescription("One fewer set for " + player.getValue()); minus.setEnabled(count > 0);
            Button plus = Ui.button(page.getContext(), "+ 1 set", Ui.PAPER, () -> safe(() -> { store.plan(plan.with(player.getKey(), count + 1), book, false); refresh.run(); }, feedback));
            plus.setContentDescription("One more set for " + player.getValue()); plus.setEnabled(count < TicketPlan.MAX_SETS);
            Ui.weighted(row, minus); Ui.weighted(row, plus); Ui.add(page, row, 52);
        }
        Ui.action(page, "Choose schemes & amounts →", Ui.GREEN, next);
    }
    static void schemes(LinearLayout page, GameStore store, String group, Consumer<String> chooseGroup, Runnable back,
                        Runnable refresh, Consumer<String> editAmount, Runnable ready, Consumer<String> feedback) {
        PrizeBook book = store.prizes(); TicketPlan plan = store.plan();
        Ui.add(page, Ui.text(page.getContext(), "2. Schemes & prize amounts", 25), -2);
        Ui.action(page, "‹ Change purchased sets", Ui.PAPER, back); totals(page, plan, book);
        Ui.add(page, Ui.text(page.getContext(), "Choose schemes, then adjust their total prizes to use the full collection. Two winners share a prize; 3+ is Bogie with no payout.", 18), -2);
        Ui.action(page, "Split full collection equally", Ui.GREEN, () -> safe(() -> { store.plan(plan, plan.balance(book), false); refresh.run(); }, feedback));
        Ui.add(page, Ui.text(page.getContext(), "Equal split uses whole rupees; any remainder adds ₹1 to the first selected schemes. You can edit each amount afterward.", 17), -2);
        Ui.action(page, "Confirm setup · ready to call", Ui.PINK, () -> safe(() -> { store.plan(plan, book, true); ready.run(); }, feedback)).setEnabled(plan.problem(book) == null);
        LinearLayout choices = new LinearLayout(page.getContext());
        Ui.weighted(choices, Ui.button(page.getContext(), "Clear selection", Ui.PAPER, () -> selectAll(store, book, plan, false, refresh, feedback)));
        Ui.weighted(choices, Ui.button(page.getContext(), "Select all", Ui.PAPER, () -> selectAll(store, book, plan, true, refresh, feedback))); Ui.add(page, choices, 52);
        List<String> groups = new ArrayList<>(PrizeCatalog.GROUPS); groups.add("Other prizes");
        for (int i = 0; i < groups.size(); i += 2) {
            LinearLayout row = new LinearLayout(page.getContext());
            for (int j = i; j < Math.min(i + 2, groups.size()); j++) {
                String name = groups.get(j); Button button = Ui.button(page.getContext(), name + (name.equals(group) ? " ✓" : ""), name.equals(group) ? Ui.PINK : Ui.PAPER, () -> chooseGroup.accept(name));
                button.setContentDescription("Setup prize group: " + name); Ui.weighted(row, button);
            }
            Ui.add(page, row, 58);
        }
        for (PrizeBook.Scheme prize : book.schemes) if (PrizeCatalog.group(book, prize).equals(group)) {
            CheckBox choice = new CheckBox(page.getContext()); choice.setText(prize.name); choice.setTextSize(21); choice.setMinHeight(Ui.dp(page.getContext(), 56));
            choice.setContentDescription("Use scheme: " + prize.name); choice.setChecked(prize.enabled);
            choice.setOnCheckedChangeListener((button, enabled) -> safe(() -> { store.plan(plan, book.configure(prize.id, prize.name, prize.rupees, enabled), false); refresh.run(); }, feedback));
            Ui.add(page, choice, -2);
            if (prize.enabled) {
                LinearLayout row = new LinearLayout(page.getContext());
                Button minus = Ui.button(page.getContext(), "− ₹5", Ui.PAPER, () -> change(store, book, plan, prize, Math.max(0, prize.rupees - 5), refresh, feedback)); minus.setEnabled(prize.rupees > 0); minus.setContentDescription("Reduce prize for " + prize.name);
                Button amount = Ui.button(page.getContext(), PrizeBook.money(prize.rupees * 100) + " · edit", Ui.PAPER, () -> editAmount.accept(prize.id)); amount.setContentDescription("Edit prize amount: " + prize.name);
                Button plus = Ui.button(page.getContext(), "+ ₹5", Ui.PAPER, () -> change(store, book, plan, prize, Math.min(100000, prize.rupees + 5), refresh, feedback)); plus.setEnabled(prize.rupees < 100000); plus.setContentDescription("Increase prize for " + prize.name);
                Ui.weighted(row, minus); Ui.weighted(row, amount); Ui.weighted(row, plus); Ui.add(page, row, 58);
            }
        }
        totals(page, plan, book);
        Ui.action(page, "Confirm setup · ready to call", Ui.PINK, () -> safe(() -> { store.plan(plan, book, true); ready.run(); }, feedback)).setEnabled(plan.problem(book) == null);
    }
    private static void change(GameStore store, PrizeBook book, TicketPlan plan, PrizeBook.Scheme prize, int amount, Runnable refresh, Consumer<String> feedback) {
        safe(() -> { store.plan(plan, book.configure(prize.id, prize.name, amount, true), false); refresh.run(); }, feedback);
    }
    private static void selectAll(GameStore store, PrizeBook book, TicketPlan plan, boolean enabled, Runnable refresh, Consumer<String> feedback) {
        safe(() -> { PrizeBook next = book; for (PrizeBook.Scheme prize : book.schemes) next = next.configure(prize.id, prize.name, prize.rupees, enabled); store.plan(plan, next, false); refresh.run(); }, feedback);
    }
}
