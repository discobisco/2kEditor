package com.disco.mystecetusnarrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NarrativeGenerator {
    private NarrativeGenerator() {}

    public static List<String> missingRequired(DetectionRecord r) {
        String[] required = {"initialTime", "species", "initialDistance", "initialBearing", "initialHeading", "finalTime", "finalDistance", "finalBearing", "finalHeading"};
        List<String> missing = new ArrayList<>();
        for (String key : required) if (blank(r.get(key))) missing.add(DetectionRecord.LABELS.get(key));
        return missing;
    }

    public static String generate(DetectionRecord r) {
        StringBuilder n = new StringBuilder();
        n.append("At ").append(utc(r.get("initialTime"))).append(", ");
        if (!blank(r.get("bestCount"))) n.append("a ").append(groupWord(r.get("species"))).append(" of ").append(r.get("bestCount")).append(' ');
        else n.append(articleFor(r.get("species"))).append(' ');
        n.append(speciesForCount(r.get("species"), r.get("bestCount"))).append(" was detected ")
            .append(distance(r.get("initialDistance"))).append(' ')
            .append(relative(r.get("initialPosition")))
            .append("at a bearing of ").append(clock(r.get("initialBearing")))
            .append(" with a heading of ").append(clock(r.get("initialHeading")));
        if (!blank(r.get("pathRelation"))) n.append(", ").append(lowerFirst(r.get("pathRelation")));
        n.append(". ");

        if (!blank(r.get("behaviors"))) {
            n.append("The ").append(animalWord(r.get("bestCount"))).append(' ')
                .append("1".equals(r.get("bestCount")) ? "was" : "were").append(" observed ")
                .append(normalizeBehavior(r.get("behaviors"))).append(". ");
        }

        if (!blank(r.get("cpaTime")) || !blank(r.get("cpaDistance"))) {
            n.append("The closest point of approach to the Rana Miller was");
            if (!blank(r.get("cpaTime"))) n.append(" at ").append(utc(r.get("cpaTime")));
            if (!blank(r.get("cpaBearing"))) n.append(" at the ").append(stripDegrees(r.get("cpaBearing"))).append(" o'clock position");
            if (!blank(r.get("cpaDistance"))) n.append(", ").append(distance(r.get("cpaDistance")));
            if (!blank(r.get("cpaPosition"))) n.append(' ').append(relative(r.get("cpaPosition")));
            if (!blank(r.get("cpaVesselHeading"))) n.append(" relative to the vessel's heading of ").append(stripDegrees(r.get("cpaVesselHeading"))).append(" degrees");
            n.append(". ");
        }

        n.append("The final detection was at ").append(utc(r.get("finalTime"))).append(' ')
            .append(distance(r.get("finalDistance"))).append(' ')
            .append(relative(r.get("finalPosition")))
            .append("with a bearing of ").append(clock(r.get("finalBearing")))
            .append(" and a heading of ").append(clock(r.get("finalHeading"))).append(". ");

        String request = r.get("mitigationRequest");
        String response = r.get("mitigationResponse");
        if (!blank(request) && !request.equalsIgnoreCase("none") && !request.equalsIgnoreCase("n/a")) {
            n.append(request).append(" mitigation was requested");
            if (!blank(r.get("requestTime"))) n.append(" at ").append(utc(r.get("requestTime")));
            if (!blank(response) && !response.equalsIgnoreCase("none") && !response.equalsIgnoreCase("n/a")) {
                n.append(" and ").append(lowerFirst(response));
                if (!blank(r.get("responseTime"))) n.append(" at ").append(utc(r.get("responseTime")));
            }
            n.append(". ");
        } else if (!blank(response) && !response.equalsIgnoreCase("none") && !response.equalsIgnoreCase("n/a")) {
            n.append(response).append(" occurred");
            if (!blank(r.get("responseTime"))) n.append(" at ").append(utc(r.get("responseTime")));
            n.append(". ");
        }

        if (!blank(r.get("pilingStatus"))) n.append(ensureSentence(r.get("pilingStatus")));
        return n.toString().replaceAll("\\s+", " ").trim();
    }

    private static String groupWord(String species) {
        String s = species.toLowerCase(Locale.US);
        return s.contains("dolphin") || s.contains("porpoise") ? "pod" : "group";
    }
    private static String speciesForCount(String species, String count) {
        if (blank(count) || "1".equals(count) || species.toLowerCase(Locale.US).endsWith("s")) return species;
        return species + "s";
    }
    private static String animalWord(String count) { return "1".equals(count.trim()) ? "animal" : "animals"; }
    private static String articleFor(String s) { return "aeiou".indexOf(Character.toLowerCase(s.charAt(0))) >= 0 ? "an" : "a"; }
    private static String distance(String s) { return s.toLowerCase(Locale.US).contains("meter") ? s : s + " meters"; }
    private static String relative(String s) { return blank(s) ? "" : (s.toLowerCase(Locale.US).startsWith("off ") ? s + " " : "off the " + s + " "); }
    private static String utc(String s) { return s.toUpperCase(Locale.US).contains("UTC") ? s : s + " UTC"; }
    private static String clock(String s) { return s.toLowerCase(Locale.US).contains("o'clock") ? s : s + " o'clock"; }
    private static String stripDegrees(String s) { return s.replace("°", "").trim(); }
    private static String normalizeBehavior(String s) { String t = lowerFirst(s.trim()); return t.startsWith("observed ") ? t.substring(9) : t; }
    private static String ensureSentence(String s) { String t = Character.toUpperCase(s.charAt(0)) + s.substring(1); return t.endsWith(".") ? t : t + "."; }
    private static String lowerFirst(String s) { return Character.toLowerCase(s.charAt(0)) + s.substring(1); }
    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
