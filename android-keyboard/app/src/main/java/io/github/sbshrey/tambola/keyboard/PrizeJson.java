package io.github.sbshrey.tambola.keyboard;

import org.json.*;
import java.util.*;

final class PrizeJson {
    static PrizeBook decode(String raw) {
        if (raw == null) return PrizeBook.defaults();
        try {
            JSONObject root = new JSONObject(raw); if (root.getInt("version") != 1) throw new JSONException("version");
            Map<String, String> players = new LinkedHashMap<>(); JSONArray people = root.getJSONArray("players");
            for (int i = 0; i < people.length(); i++) { JSONObject item = people.getJSONObject(i); if (players.put(item.getString("id"), item.getString("name")) != null) throw new JSONException("duplicate"); }
            List<PrizeBook.Scheme> schemes = new ArrayList<>(); JSONArray items = root.getJSONArray("schemes");
            for (int i = 0; i < items.length(); i++) { JSONObject item = items.getJSONObject(i); List<String> winners = new ArrayList<>(); JSONArray ids = item.getJSONArray("winners"); for (int j = 0; j < ids.length(); j++) winners.add(ids.getString(j));
                schemes.add(new PrizeBook.Scheme(item.getString("id"), item.getString("name"), item.getInt("rupees"), item.getBoolean("enabled"), item.getInt("minimum"), winners, item.getInt("award"), item.getInt("count"))); }
            return new PrizeBook(players, schemes);
        } catch (JSONException | IllegalArgumentException error) { throw new IllegalArgumentException("Prize details cannot be read. Your numbers are safe."); }
    }
    static String encode(PrizeBook book) {
        try {
            JSONObject root = new JSONObject().put("version", 1); JSONArray players = new JSONArray(), schemes = new JSONArray();
            for (Map.Entry<String, String> person : book.players.entrySet()) players.put(new JSONObject().put("id", person.getKey()).put("name", person.getValue()));
            for (PrizeBook.Scheme item : book.schemes) schemes.put(new JSONObject().put("id", item.id).put("name", item.name).put("rupees", item.rupees).put("enabled", item.enabled).put("minimum", item.minimumCalls).put("winners", new JSONArray(item.winners)).put("award", item.awardedRupees).put("count", item.callCount));
            return root.put("players", players).put("schemes", schemes).toString();
        } catch (JSONException error) { throw new IllegalStateException("Could not save prize details."); }
    }
}
