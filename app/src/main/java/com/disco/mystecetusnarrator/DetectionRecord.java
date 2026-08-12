package com.disco.mystecetusnarrator;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DetectionRecord {
    public static final LinkedHashMap<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("vNumber", "Detection ID (example: V339)");
        LABELS.put("initialTime", "Initial detection time (UTC)");
        LABELS.put("species", "Species");
        LABELS.put("bestCount", "Best count");
        LABELS.put("initialDistance", "Initial distance (meters)");
        LABELS.put("initialPosition", "Initial relative position (bow, port bow, etc.)");
        LABELS.put("initialBearing", "Initial bearing (example: 188 + 11:30)");
        LABELS.put("initialHeading", "Initial heading (example: 188 + 1:00)");
        LABELS.put("pathRelation", "Relationship to vessel path");
        LABELS.put("behaviors", "Observed behaviors");
        LABELS.put("separationOutcome", "Separation distance outcome");
        LABELS.put("cpaTime", "CPA time (UTC)");
        LABELS.put("cpaDistance", "CPA distance (meters)");
        LABELS.put("cpaPosition", "CPA relative position");
        LABELS.put("cpaBearing", "CPA bearing / clock position");
        LABELS.put("cpaVesselHeading", "Vessel heading at CPA (degrees)");
        LABELS.put("finalTime", "Final detection time (UTC)");
        LABELS.put("finalDistance", "Final distance (meters)");
        LABELS.put("finalPosition", "Final relative position");
        LABELS.put("finalBearing", "Final bearing");
        LABELS.put("finalHeading", "Final heading");
        LABELS.put("mitigationRequest", "Mitigation requested (None, VSA, shutdown, etc.)");
        LABELS.put("requestTime", "Time of mitigation request (UTC)");
        LABELS.put("mitigationResponse", "Vessel response");
        LABELS.put("responseTime", "Time of mitigation response (UTC)");
        LABELS.put("pilingStatus", "Piling status / detection delay");
        LABELS.put("rawOcr", "OCR source text (review only)");
    }

    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();

    public DetectionRecord() {
        for (String key : LABELS.keySet()) values.put(key, "");
        values.put("mitigationRequest", "None");
    }

    public String get(String key) { return values.getOrDefault(key, "").trim(); }
    public void set(String key, String value) { if (values.containsKey(key)) values.put(key, value == null ? "" : value.trim()); }
    public Map<String, String> values() { return values; }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> item : values.entrySet()) json.put(item.getKey(), item.getValue());
        return json;
    }

    public static DetectionRecord fromJson(JSONObject json) {
        DetectionRecord record = new DetectionRecord();
        for (String key : LABELS.keySet()) record.set(key, json.optString(key, ""));
        return record;
    }
}
