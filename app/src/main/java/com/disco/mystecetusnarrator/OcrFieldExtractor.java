package com.disco.mystecetusnarrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative extraction: ambiguous values stay blank for manual review. */
public final class OcrFieldExtractor {
    public static final class Result {
        public final DetectionRecord record;
        public final List<String> warnings;
        Result(DetectionRecord record, List<String> warnings) { this.record = record; this.warnings = warnings; }
    }

    private OcrFieldExtractor() {}

    public static Result extract(String rawText) {
        DetectionRecord r = new DetectionRecord();
        List<String> warnings = new ArrayList<>();
        r.set("rawOcr", rawText);

        Matcher ids = Pattern.compile("\\bV\\s?(\\d{3,5})\\b", Pattern.CASE_INSENSITIVE).matcher(rawText);
        List<String> foundIds = new ArrayList<>();
        while (ids.find()) if (!foundIds.contains("V" + ids.group(1))) foundIds.add("V" + ids.group(1));
        if (foundIds.size() == 1) r.set("vNumber", foundIds.get(0));
        else if (foundIds.size() > 1) warnings.add("Multiple detection IDs were found. Enter the intended V-number manually.");

        Matcher iso = Pattern.compile("20\\d{2}-\\d{2}-\\d{2}[ T](\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)\\s*UTC", Pattern.CASE_INSENSITIVE).matcher(rawText);
        List<String> times = new ArrayList<>();
        while (iso.find()) if (!times.contains(iso.group(1))) times.add(iso.group(1));
        if (times.size() == 2) {
            r.set("initialTime", hhmm(times.get(0)));
            r.set("finalTime", hhmm(times.get(1)));
        } else if (!times.isEmpty()) {
            warnings.add("The UTC timestamps were ambiguous. Enter initial, CPA, final, and mitigation times manually.");
        }

        String lower = rawText.toLowerCase(Locale.US);
        String[] species = {"short-beaked common dolphin", "common dolphin", "minke whale", "fin whale", "humpback whale", "right whale", "unidentified mysticete whale", "unidentified mysticete", "harbor porpoise"};
        for (String candidate : species) if (lower.contains(candidate)) { r.set("species", titleSpecies(candidate)); break; }

        Matcher meters = Pattern.compile("(?<![\\d.])(\\d{1,4}(?:\\.\\d{1,2})?)\\s*m(?:eters?)?\\b", Pattern.CASE_INSENSITIVE).matcher(rawText);
        List<String> distances = new ArrayList<>();
        while (meters.find()) if (!distances.contains(meters.group(1))) distances.add(meters.group(1));
        if (distances.size() == 1) r.set("initialDistance", distances.get(0));
        else if (distances.size() > 1) warnings.add("Multiple distances were found. Assign initial, CPA, and final distances on the review form.");

        Matcher count = Pattern.compile("\\b(?:Adults?|Best Count)\\s*[:=]?\\s*(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE).matcher(rawText);
        if (count.find()) r.set("bestCount", count.group(1));

        if (lower.contains("voluntary approach")) r.set("behaviors", "voluntarily approaching the vessel");
        if (lower.contains("bow rid")) append(r, "behaviors", "bow riding");
        if (lower.contains("porpois")) append(r, "behaviors", "porpoising");
        if (lower.contains("feeding")) append(r, "behaviors", "feeding");
        if (lower.contains("travel")) append(r, "behaviors", "traveling");

        if (lower.contains("engine neutral")) r.set("mitigationResponse", "the engine was shifted to neutral");
        if (lower.contains("no active piling")) r.set("pilingStatus", "No piling mitigation was needed as there was no active piling during the detection");
        if (lower.contains("detection delay")) appendSentence(r, "pilingStatus", "piling was under a detection delay");

        warnings.add("OCR never finalizes a narrative automatically. Review every extracted value against the photos.");
        return new Result(r, warnings);
    }

    private static String hhmm(String time) { return time.length() >= 5 ? time.substring(0, 5) : time; }
    private static String titleSpecies(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static void append(DetectionRecord r, String key, String value) { String old = r.get(key); r.set(key, old.isEmpty() ? value : old + ", and " + value); }
    private static void appendSentence(DetectionRecord r, String key, String value) { String old = r.get(key); r.set(key, old.isEmpty() ? value : old + ", and " + value); }
}
