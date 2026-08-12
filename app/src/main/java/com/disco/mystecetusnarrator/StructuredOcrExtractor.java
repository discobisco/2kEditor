package com.disco.mystecetusnarrator;

import android.graphics.Rect;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads Mystecetus tables by preserving the position of OCR lines. */
public final class StructuredOcrExtractor {
    public static final class Cell {
        final String text; final float x; final float y; final float height;
        Cell(String text, Rect box) { this.text = text.trim(); x = box.exactCenterX(); y = box.exactCenterY(); height = box.height(); }
    }
    public static final class Page {
        final String rawText; final List<Cell> cells;
        private Page(String rawText, List<Cell> cells) { this.rawText = rawText; this.cells = cells; }
        public static Page from(Text text) {
            List<Cell> cells = new ArrayList<>();
            for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines())
                if (line.getBoundingBox() != null && !line.getText().trim().isEmpty()) cells.add(new Cell(line.getText(), line.getBoundingBox()));
            return new Page(text.getText(), cells);
        }
    }
    public static final class Result {
        public final DetectionRecord record; public final List<String> warnings;
        Result(DetectionRecord record, List<String> warnings) { this.record = record; this.warnings = warnings; }
    }

    private static final Pattern ID = Pattern.compile("\\bV\\s?(\\d{3,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final LinkedHashMap<String, List<String>> HEADERS = new LinkedHashMap<>();
    static {
        HEADERS.put("initialTime", Arrays.asList("time", "initial time"));
        HEADERS.put("finalTime", Arrays.asList("end time", "final time"));
        HEADERS.put("initialDistance", Arrays.asList("initial detection distance"));
        HEADERS.put("finalDistance", Arrays.asList("final detection distance"));
        HEADERS.put("species", Arrays.asList("species"));
        HEADERS.put("initialBearing", Arrays.asList("bearing to initial"));
        HEADERS.put("cpaBearing", Arrays.asList("bearing to cpa"));
        HEADERS.put("finalBearing", Arrays.asList("bearing to final"));
        HEADERS.put("initialHeading", Arrays.asList("initial where to"));
        HEADERS.put("finalHeading", Arrays.asList("final where to"));
        HEADERS.put("bestCount", Arrays.asList("best count", "adults"));
        HEADERS.put("cpaDistance", Arrays.asList("cpa to vessel"));
        HEADERS.put("cpaTime", Arrays.asList("time of cpa to active pile", "time of cpa"));
        HEADERS.put("mitigationRequest", Arrays.asList("mitigation request"));
        HEADERS.put("requestTime", Arrays.asList("time of mitigation request"));
        HEADERS.put("mitigationResponse", Arrays.asList("mitigation response"));
        HEADERS.put("responseTime", Arrays.asList("time of mitigation response"));
        HEADERS.put("behaviors", Arrays.asList("behavior notes", "behavior"));
        HEADERS.put("pathRelation", Arrays.asList("strike avoidance mitigation notes"));
        HEADERS.put("pilingStatus", Arrays.asList("other mitigation notes"));
    }

    private StructuredOcrExtractor() {}

    public static List<String> findDetectionIds(List<Page> pages) {
        Set<String> ids = new LinkedHashSet<>();
        for (Page page : pages) { Matcher m = ID.matcher(page.rawText); while (m.find()) ids.add("V" + m.group(1)); }
        return new ArrayList<>(ids);
    }

    public static Result extract(List<Page> pages, String targetId) {
        DetectionRecord out = new DetectionRecord();
        out.set("vNumber", targetId);
        List<String> warnings = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        int matchedPages = 0;
        for (Page page : pages) {
            raw.append("\n\n--- PHOTO ---\n").append(page.rawText);
            Cell idCell = findIdCell(page, targetId);
            if (idCell == null) continue;
            matchedPages++;
            Map<String, Cell> headerCells = findHeaders(page, idCell.y);
            float tolerance = Math.max(16f, idCell.height * 1.35f);
            for (Map.Entry<String, Cell> header : headerCells.entrySet()) {
                Cell value = nearestRowCell(page, header.getValue(), idCell, tolerance, headerCells.values());
                if (value != null) assign(out, header.getKey(), value.text);
            }
            enrichFromTargetRow(out, page, idCell, tolerance);
        }
        out.set("rawOcr", raw.toString().trim());
        if (matchedPages < pages.size()) warnings.add((pages.size() - matchedPages) + " photo(s) did not contain " + targetId + " and were ignored.");
        warnings.add("Review every extracted value against the selected " + targetId + " row before generating.");
        return new Result(out, warnings);
    }

    private static Cell findIdCell(Page page, String target) {
        String normalized = target.replace(" ", "").toUpperCase(Locale.US);
        for (Cell c : page.cells) {
            Matcher m = ID.matcher(c.text);
            if (m.find() && ("V" + m.group(1)).equals(normalized)) return c;
        }
        return null;
    }

    private static Map<String, Cell> findHeaders(Page page, float rowY) {
        Map<String, Cell> found = new LinkedHashMap<>();
        for (Cell c : page.cells) {
            if (c.y >= rowY) continue;
            String normalized = normalize(c.text);
            for (Map.Entry<String, List<String>> field : HEADERS.entrySet()) {
                for (String alias : field.getValue()) if (normalized.equals(alias) || (!alias.equals("time") && normalized.contains(alias))) {
                    Cell old = found.get(field.getKey());
                    if (old == null || c.y > old.y) found.put(field.getKey(), c);
                    break;
                }
            }
        }
        return found;
    }

    private static Cell nearestRowCell(Page page, Cell header, Cell id, float tolerance, Iterable<Cell> headers) {
        Cell best = null; float bestDx = Float.MAX_VALUE;
        for (Cell c : page.cells) {
            if (c == id || Math.abs(c.y - id.y) > tolerance || c.text.matches("(?i)V\\s?\\d{3,5}")) continue;
            boolean isHeader = false; for (Cell h : headers) if (c == h) { isHeader = true; break; }
            if (isHeader) continue;
            float dx = Math.abs(c.x - header.x);
            if (dx < bestDx) { bestDx = dx; best = c; }
        }
        return best;
    }

    private static void assign(DetectionRecord r, String key, String value) {
        String cleaned = clean(value);
        if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("N/A") || cleaned.equals("...")) return;
        if (key.toLowerCase(Locale.US).contains("time")) cleaned = time(cleaned);
        if (key.toLowerCase(Locale.US).contains("distance")) cleaned = cleaned.replaceAll("(?i)\\s*m(?:eters?)?\\s*$", "").trim();
        if (key.equals("mitigationResponse") && cleaned.equalsIgnoreCase("Engine Neutral")) cleaned = "the engine was shifted to neutral";
        if (key.equals("mitigationRequest") && cleaned.equalsIgnoreCase("Engine Neutral")) cleaned = "engine neutral";
        if (r.get(key).isEmpty() || r.get(key).equalsIgnoreCase("None")) r.set(key, cleaned);
    }

    private static void enrichFromTargetRow(DetectionRecord r, Page page, Cell id, float tolerance) {
        StringBuilder row = new StringBuilder();
        for (Cell c : page.cells) if (Math.abs(c.y - id.y) <= tolerance) row.append(' ').append(c.text);
        String text = row.toString(); String lower = text.toLowerCase(Locale.US);
        String[] species = {"short-beaked common dolphin", "common dolphin", "minke whale", "fin whale", "humpback whale", "right whale", "unidentified mysticete whale", "unidentified mysticete", "harbor porpoise"};
        for (String candidate : species) if (lower.contains(candidate) && r.get("species").isEmpty()) r.set("species", Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1));
        if (lower.contains("voluntary approach")) append(r, "behaviors", "voluntarily approaching the vessel");
        if (lower.contains("bow rid")) append(r, "behaviors", "bow riding");
        if (lower.contains("porpois")) append(r, "behaviors", "porpoising");
        if (lower.contains("feeding")) append(r, "behaviors", "feeding");
        if (lower.contains("travel")) append(r, "behaviors", "traveling");
        if (lower.contains("no active piling")) r.set("pilingStatus", "No piling mitigation was needed as there was no active piling during the detection");
        if (lower.contains("detection delay") && !r.get("pilingStatus").toLowerCase(Locale.US).contains("detection delay")) append(r, "pilingStatus", "piling was under a detection delay");
    }

    private static String normalize(String s) { return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }
    private static String clean(String s) { return s.replace('°', ' ').replaceAll("\\s+", " ").trim(); }
    private static String time(String s) { Matcher m = Pattern.compile("(?:20\\d{2}-\\d{2}-\\d{2}[ T])?(\\d{2}:\\d{2})(?::\\d{2}(?:\\.\\d+)?)?", Pattern.CASE_INSENSITIVE).matcher(s); return m.find() ? m.group(1) : s; }
    private static void append(DetectionRecord r, String key, String value) { String old = r.get(key); if (!old.toLowerCase(Locale.US).contains(value.toLowerCase(Locale.US))) r.set(key, old.isEmpty() ? value : old + ", and " + value); }
}
