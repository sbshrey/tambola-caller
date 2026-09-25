package io.github.sbshrey.tambola.keyboard;

import java.util.*;

/** Host-supplied prize names. These are choices, not automatic ticket rules. */
final class PrizeCatalog {
    static final class Preset {
        final String id, name, group;
        final int minimum;
        Preset(String id, String name, String group, int minimum) { this.id = id; this.name = name; this.group = group; this.minimum = minimum; }
    }
    static final List<String> GROUPS = Collections.unmodifiableList(Arrays.asList("Quick prizes", "Lines", "Boxes", "Special prizes", "Houses"));
    static final List<Preset> ALL;
    static {
        List<Preset> presets = new ArrayList<>();
        presets.add(new Preset("prize-0", "Early 5", "Quick prizes", 5));
        String[] quick = {"King", "Queen", "Temperature with ladoo", "Bamboo", "Pair", "Sona", "Mona", "Corner"};
        for (String name : quick) presets.add(preset(name, "Quick prizes", 1));
        String[] ordinals = {"First", "Second", "Third", "Fourth", "Fifth", "Sixth", "Seventh", "Eighth", "Ninth"};
        for (String ordinal : ordinals) presets.add(preset(ordinal + " line", "Lines", 5));
        for (String ordinal : ordinals) presets.add(preset(ordinal + " box", "Boxes", 1));
        presets.add(preset("Early 10", "Special prizes", 10));
        String[] special = {"Novelty", "KPN", "I love you", "L", "Photo frame", "Honeymoon", "Pati patni or vo", "Siddi family planning", "Ulti family planning", "Gharwali baharwali"};
        for (String name : special) presets.add(preset(name, "Special prizes", 1));
        for (int i = 1; i <= 3; i++) presets.add(preset("House " + i, "Houses", 15));
        ALL = Collections.unmodifiableList(presets);
    }
    private static Preset preset(String name, String group, int minimum) { return new Preset("catalog-" + name.toLowerCase(Locale.ROOT).replace(' ', '-'), name, group, minimum); }
    static Preset find(String id) { for (Preset item : ALL) if (item.id.equals(id)) return item; throw new IllegalArgumentException("Prize choice not found."); }
    static PrizeBook.Scheme existing(PrizeBook book, Preset preset) {
        for (PrizeBook.Scheme item : book.schemes) if (item.id.equals(preset.id)) return item;
        for (PrizeBook.Scheme item : book.schemes) if (item.name.equalsIgnoreCase(preset.name)) return item;
        return null;
    }
    static List<String> allIds() { List<String> ids = new ArrayList<>(); for (Preset item : ALL) ids.add(item.id); return ids; }
}
