package io.github.sbshrey.tambola.keyboard;

import org.json.*;
import java.util.*;

final class TicketPlanJson {
    static String encode(TicketPlan plan) {
        try {
            JSONObject root = new JSONObject().put("version", 1).put("approved", plan.approved);
            JSONObject sets = new JSONObject(), prizes = new JSONObject();
            for (Map.Entry<String, Integer> item : plan.sets.entrySet()) sets.put(item.getKey(), item.getValue());
            for (Map.Entry<String, Integer> item : plan.allocations.entrySet()) prizes.put(item.getKey(), item.getValue());
            return root.put("sets", sets).put("prizes", prizes).toString();
        } catch (JSONException error) { throw new IllegalStateException("Could not save the ticket plan."); }
    }
    static TicketPlan decode(String raw) {
        if (raw == null) return null;
        try {
            JSONObject root = new JSONObject(raw); if (root.getInt("version") != 1) throw new JSONException("version");
            return new TicketPlan(values(root.getJSONObject("sets")), values(root.getJSONObject("prizes")), root.getBoolean("approved"));
        } catch (JSONException | IllegalArgumentException error) { throw new IllegalArgumentException("Ticket plan cannot be read. Open game setup before continuing."); }
    }
    private static Map<String, Integer> values(JSONObject object) throws JSONException {
        Map<String, Integer> values = new LinkedHashMap<>(); Iterator<String> keys = object.keys();
        while (keys.hasNext()) { String key = keys.next(); Object value = object.get(key); if (!(value instanceof Integer)) throw new JSONException("whole number required"); values.put(key, (Integer) value); }
        return values;
    }
}
