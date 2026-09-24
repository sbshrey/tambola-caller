package io.github.sbshrey.tambola.keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Immutable, local round. A draw records a call, not a delivered WhatsApp message. */
public final class Game {
    public static final int TOTAL = 90;
    private final List<Integer> called;

    private Game(List<Integer> numbers) { called = Collections.unmodifiableList(new ArrayList<>(numbers)); }
    public static Game empty() { return new Game(Collections.emptyList()); }
    public static Game decode(String value) {
        if (value == null || value.isEmpty()) return empty();
        List<Integer> numbers = new ArrayList<>();
        for (String part : value.split(",", -1)) {
            if (!part.matches("[1-9][0-9]?")) throw new IllegalArgumentException("Unreadable saved game");
            int number = Integer.parseInt(part);
            if (number > TOTAL || numbers.contains(number)) throw new IllegalArgumentException("Unreadable saved game");
            numbers.add(number);
        }
        return new Game(numbers);
    }
    public String encode() {
        StringBuilder text = new StringBuilder();
        for (int number : called) { if (text.length() > 0) text.append(','); text.append(number); }
        return text.toString();
    }
    public Game draw(Random random) {
        if (called.size() == TOTAL) return this;
        List<Integer> remaining = new ArrayList<>();
        for (int i = 1; i <= TOTAL; i++) if (!called.contains(i)) remaining.add(i);
        List<Integer> next = new ArrayList<>(called);
        next.add(remaining.get(random.nextInt(remaining.size())));
        return new Game(next);
    }
    public Game undo() { return called.isEmpty() ? this : new Game(called.subList(0, called.size() - 1)); }
    public List<Integer> called() { return called; }
    public int count() { return called.size(); }
    public int latest() { return called.isEmpty() ? 0 : called.get(called.size() - 1); }
    public String recent() {
        StringBuilder text = new StringBuilder();
        for (int i = called.size() - 1; i >= Math.max(0, called.size() - 10); i--) {
            if (text.length() > 0) text.append("  ·  ");
            text.append(called.get(i));
        }
        return text.length() == 0 ? "No numbers yet" : text.toString();
    }
    public static String emoji(int number) {
        if (number < 1 || number > TOTAL) throw new IllegalArgumentException("Number must be 1–90");
        StringBuilder text = new StringBuilder();
        for (char digit : Integer.toString(number).toCharArray()) text.append(digit).append('\uFE0F').append('\u20E3');
        return text.toString();
    }
}
