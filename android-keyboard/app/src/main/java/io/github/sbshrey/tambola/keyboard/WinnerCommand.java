package io.github.sbshrey.tambola.keyboard;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** A narrow spoken command: one enabled prize, followed (or preceded) by one or two names. */
final class WinnerCommand {
    static final String EXAMPLE = "Say the prize and name, like Early five winner Asha Sharma.";
    private static final Pattern WINNER_MARKER = Pattern.compile("(?iu)^(?:(?:ki|ke|ka|के|की|का)\\s+)?(?:winners?|विनर|विनर्स|विजेता)(?:\\s|[:,]|$)");
    final PrizeBook book;
    final String announcement;
    private WinnerCommand(PrizeBook book, String id) { this.book = book; announcement = book.announcement(book.scheme(id)); }

    static WinnerCommand prepare(PrizeBook book, String speech, int count) {
        if (speech == null || speech.length() > 250) throw new IllegalArgumentException(EXAMPLE);
        String text = Normalizer.normalize(speech, Normalizer.Form.NFC).trim().replaceAll("[।.!?]+$", "").trim();
        Map<String, String> matches = new LinkedHashMap<>(), explicit = new LinkedHashMap<>();
        for (PrizeBook.Scheme scheme : book.schemes) if (scheme.enabled) {
            int longest = -1; String remaining = null; boolean markedWinner = false;
            for (String alias : aliases(scheme.name)) {
                String literal = Pattern.quote(alias).replace(" ", "\\E\\s+\\Q");
                Pattern start = Pattern.compile("^(?:prize\\s+)?" + literal + "(?=\\s|[:,]|$)[\\s:,]*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                Pattern end = Pattern.compile("^(.*?)\\s+" + literal + "$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                Matcher m = start.matcher(text), tail = end.matcher(text);
                boolean prefix = m.matches();
                String rest = prefix ? m.group(1) : tail.matches() ? tail.group(1) : null;
                if (rest != null && alias.length() > longest) { longest = alias.length(); remaining = rest; markedWinner = prefix && WINNER_MARKER.matcher(rest).find(); }
            }
            if (remaining != null) { matches.put(scheme.id, remaining); if (markedWinner) explicit.put(scheme.id, remaining); }
        }
        // With an explicit winner marker, a person's name (e.g. Mona) may also be a prize name.
        if (!explicit.isEmpty()) matches = explicit;
        if (matches.size() != 1) throw new IllegalArgumentException(matches.isEmpty() ? "Prize not clear. Choose prize schemes to enable it. " + EXAMPLE : "More than one prize heard. Say one prize and its winner.");
        String id = matches.keySet().iterator().next(); PrizeBook.Scheme scheme = book.scheme(id);
        if (!scheme.winners.isEmpty()) throw new IllegalArgumentException(scheme.name + " already has winners. Use its prize button to correct them, or Insert announcement.");
        if (count < scheme.minimumCalls) throw new IllegalArgumentException("Call at least " + scheme.minimumCalls + " numbers before awarding " + scheme.name + ".");
        String names = matches.get(id).replaceFirst("(?iu)^(?:(?:ki|ke|ka|के|की|का)\\s+)?(?:(?:winners?|विनर|विनर्स|विजेता)(?:\\s+(?:is|are|hai|hain|है|हैं))?\\s*[:,-]?\\s*|(?:ki|ke|ka|के|की|का)\\s+)", "")
            .replaceFirst("(?iu)\\s+(?:ki|ke|ka|के|की|का)$", "").trim();
        String[] people = names.split("(?iu)\\s+(?:and|aur|और|एंड)\\s+|\\s*[&,]\\s*", -1);
        if (people.length > 2) throw new IllegalArgumentException("Say up to two winners, joined by and or aur.");
        Map<String, String> players = new LinkedHashMap<>(book.players); List<String> winners = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (String person : people) {
            String name = PrizeBook.clean(person);
            if (!name.matches("[\\p{L}\\p{M} .’'\\-]+") || name.split("\\s+").length > 8 || name.matches("(?iu)(?:winner|winners|विनर|विजेता|and|aur|और|hai|है)")) throw new IllegalArgumentException(EXAMPLE);
            String key = key(name); if (!seen.add(key)) throw new IllegalArgumentException("Say two different winner names.");
            String playerId = null;
            for (Map.Entry<String, String> player : players.entrySet()) if (key(player.getValue()).equals(key)) { playerId = player.getKey(); break; }
            if (playerId == null) { playerId = UUID.randomUUID().toString(); players.put(playerId, name); }
            winners.add(playerId);
        }
        PrizeBook updated = new PrizeBook(players, book.schemes).award(id, winners, scheme.rupees, count);
        return new WinnerCommand(updated, id);
    }
    private static String key(String name) { return Normalizer.normalize(PrizeBook.clean(name), Normalizer.Form.NFC).toLowerCase(Locale.ROOT); }
    private static List<String> aliases(String name) {
        List<String> result = new ArrayList<>(); result.add(name);
        String key = name.toLowerCase(Locale.ROOT);
        String[] english = {"first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth"};
        String[] hindi = {"पहली", "दूसरी", "तीसरी", "चौथी", "पांचवी", "छठी", "सातवीं", "आठवीं", "नौवीं"};
        String[] roman = {"pehli", "dusri", "tisri", "chauthi", "panchvi", "chhathi", "satvi", "aathvi", "nauvi"};
        String[] spokenEnglish = {"फर्स्ट", "सेकंड", "थर्ड", "फोर्थ", "फिफ्थ", "सिक्स्थ", "सेवंथ", "एट्थ", "नाइंथ"};
        for (int i = 0; i < 9; i++) for (String type : new String[]{"line", "box"}) if (key.equals(english[i] + " " + type)) {
            String translated = type.equals("line") ? "लाइन" : "बॉक्स";
            result.add((i + 1) + " " + type); result.add(roman[i] + " " + type); result.add(hindi[i] + " " + translated);
            result.add(english[i] + " " + translated);
            result.add(spokenEnglish[i] + " " + translated);
        }
        switch (key) {
            case "early 5": add(result, "early five|अर्ली फाइव|अर्ली 5|अर्ली पांच|अर्ली पाँच"); break;
            case "early 10": add(result, "early ten|अर्ली टेन|अर्ली 10|अर्ली दस"); break;
            case "king": add(result, "किंग"); break;
            case "queen": add(result, "क्वीन"); break;
            case "temperature with ladoo": add(result, "temperature with laddu|temperature with laddoo|टेंपरेचर विद लड्डू|टेम्परेचर विद लड्डू"); break;
            case "bamboo": add(result, "बैंबू|बम्बू|बांबू"); break;
            case "pair": add(result, "पेयर"); break;
            case "sona": add(result, "सोना"); break;
            case "mona": add(result, "मोना"); break;
            case "corner": add(result, "कॉर्नर|कार्नर"); break;
            case "novelty": add(result, "नोवेल्टी|नॉवेल्टी"); break;
            case "kpn": add(result, "k p n|के पी एन|केपीएन"); break;
            case "i love you": add(result, "आई लव यू"); break;
            case "l": add(result, "एल"); break;
            case "photo frame": add(result, "फोटो फ्रेम"); break;
            case "honeymoon": add(result, "हनीमून"); break;
            case "pati patni or vo": add(result, "pati patni aur woh|pati patni aur vo|पति पत्नी और वो|पति पत्नी और वह"); break;
            case "siddi family planning": add(result, "seedhi family planning|sidhi family planning|सीधी फैमिली प्लानिंग"); break;
            case "ulti family planning": add(result, "उल्टी फैमिली प्लानिंग"); break;
            case "gharwali baharwali": add(result, "ghar wali bahar wali|घरवाली बाहरवाली|घर वाली बाहर वाली"); break;
            case "house 1": add(result, "house one|हाउस वन|हाउस 1|हाउस एक"); break;
            case "house 2": add(result, "house two|हाउस टू|हाउस 2|हाउस दो"); break;
            case "house 3": add(result, "house three|हाउस थ्री|हाउस 3|हाउस तीन"); break;
            case "top line": add(result, "टॉप लाइन"); break;
            case "middle line": add(result, "मिडिल लाइन"); break;
            case "bottom line": add(result, "बॉटम लाइन"); break;
            case "full house": add(result, "फुल हाउस"); break;
        }
        return result;
    }
    private static void add(List<String> list, String values) { list.addAll(Arrays.asList(values.split("\\|"))); }
}
