package com.disco.mystecetusnarrator;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class HistoryStore {
    private static final String PREFS = "detection_history";
    private static final String KEY = "records";
    private final SharedPreferences prefs;

    public HistoryStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public List<DetectionRecord> load() {
        List<DetectionRecord> records = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) records.add(DetectionRecord.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) {}
        return records;
    }

    public void save(DetectionRecord record) {
        List<DetectionRecord> records = load();
        String id = record.get("vNumber");
        records.removeIf(existing -> !id.isEmpty() && existing.get("vNumber").equalsIgnoreCase(id));
        records.add(0, record);
        JSONArray array = new JSONArray();
        try { for (DetectionRecord item : records) array.put(item.toJson()); } catch (Exception ignored) {}
        prefs.edit().putString(KEY, array.toString()).apply();
    }
}
