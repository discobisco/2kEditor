package com.disco.mystecetusnarrator;

import android.graphics.Rect;
import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mystecetus-specific table reader. It follows photographed row slope and column bounds. */
public final class StructuredOcrExtractor {
    public static final class Cell {
        final String text;
        final float left, right, x, y, height;
        final boolean line;

        Cell(String text, Rect box, boolean line) {
            this.text = text.trim();
            this.left = box.left;
            this.right = box.right;
            this.x = box.exactCenterX();
            this.y = box.exactCenterY();
            this.height = Math.max(1, box.height());
            this.line = line;
        }
    }

    public static final class Page {
        final String rawText;
        final List<Cell> cells;
        final float width;

        private Page(String rawText, List<Cell> cells, float width) {
            this.rawText = rawText;
            this.cells = cells;
            this.width = width;
        }

        public static Page from(Text text) {
            List<Cell> cells = new ArrayList<>();
            float width = 1;
            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    Rect lineBox = line.getBoundingBox();
                    if (lineBox != null && !line.getText().trim().isEmpty()) {
                        cells.add(new Cell(line.getText(), lineBox, true));
                        width = Math.max(width, lineBox.right);
                    }
                    for (Text.Element element : line.getElements()) {
                        Rect box = element.getBoundingBox();
                        if (box != null && !element.getText().trim().isEmpty()) {
                            cells.add(new Cell(element.getText(), box, false));
                            width = Math.max(width, box.right);
                        }
                    }
                }
            }
            return new Page(text.getText(), cells, width);
        }
    }

    public static final class Result {
        public final DetectionRecord record;
        public final List<String> warnings;
        Result(DetectionRecord record, List<String> warnings) {
            this.record = record;
            this.warnings = warnings;
        }
    }

    private static final class Header {
        final String key;
        final Cell cell;
        float left, right;
        Header(String key, Cell cell) { this.key = key; this.cell = cell; }
    }

    private static final class Candidate {
        final String value;
        final int score;
        Candidate(String value, int score) { this.value = value; this.score = score; }
    }

    private static final Pattern ID = Pattern.compile("\\bV\\s?(\\d{3,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final LinkedHashMap<String, List<String>> HEADERS = new LinkedHashMap<>();
    static {
        HEADERS.put("initialTime", Arrays.asList("initial time", "time"));
        HEADERS.put("finalTime", Arrays.asList("final time", "end time"));
        HEADERS.put("initialDistance", Arrays.asList("initial detection distance", "initial distance"));
        HEADERS.put("finalDistance", Arrays.asList("final detection distance", "final distance"));
        HEADERS.put("species", Arrays.asList("species"));
        HEADERS.put("initialBearing", Arrays.asList("bearing to initial", "initial bearing"));
        HEADERS.put("cpaBearing", Arrays.asList("bearing to cpa", "cpa bearing"));
        HEADERS.put("finalBearing", Arrays.asList("bearing to final", "final bearing"));
        HEADERS.put("initialHeading", Arrays.asList("initial where to", "initial heading"));
        HEADERS.put("finalHeading", Arrays.asList("final where to", "final heading"));
        HEADERS.put("bestCount", Arrays.asList("best count", "adults"));
        HEADERS.put("cpaDistance", Arrays.asList("cpa to vessel", "cpa distance"));
        HEADERS.put("cpaTime", Arrays.asList("time of cpa to active pile", "time of cpa", "cpa time"));
        HEADERS.put("mitigationRequest", Arrays.asList("mitigation request"));
        HEADERS.put("requestTime", Arrays.asList("time of mitigation request", "request time"));
        HEADERS.put("mitigationResponse", Arrays.asList("mitigation response"));
        HEADERS.put("responseTime", Arrays.asList("time of mitigation response", "response time"));
        HEADERS.put("behaviors", Arrays.asList("behavior notes", "behavior"));
        HEADERS.put("separationOutcome", Arrays.asList("strike avoidance mitigation notes"));
        HEADERS.put("pathRelation", Arrays.asList("strike avoidance mitigation notes"));
        HEADERS.put("pilingStatus", Arrays.asList("other mitigation notes"));
    }

    private StructuredOcrExtractor() {}

    public static List<String> findDetectionIds(List<Page> pages) {
        Set<String> ids = new LinkedHashSet<>();
        for (Page page : pages) {
            Matcher matcher = ID.matcher(page.rawText);
            while (matcher.find()) ids.add("V" + matcher.group(1));
        }
        return new ArrayList<>(ids);
    }

    public static Result extract(List<Page> pages, String targetId) {
        DetectionRecord out = new DetectionRecord();
        out.set("vNumber", targetId);
        Map<String, Candidate> best = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        int matchedPages = 0;

        for (Page page : pages) {
            raw.append("\n\n--- PHOTO ---\n").append(page.rawText);
            Cell id = findIdCell(page, targetId);
            if (id == null) continue;
            matchedPages++;

            List<Header> headers = findHeaders(page, id.y);
            if (headers.isEmpty()) continue;
            setColumnBounds(headers, page.width);
            float slope = estimateHeaderSlope(headers);
            float rowHeight = estimateRowHeight(page, id);

            for (Header header : headers) {
                String value = readBoundedCell(page, header, id, slope, rowHeight, headers);
                if (!value.isEmpty()) offer(best, header.key, value);
            }
            enrichFromTargetRow(best, page, id, slope, rowHeight);
        }

        for (Map.Entry<String, Candidate> item : best.entrySet()) assign(out, item.getKey(), item.getValue().value);
        out.set("rawOcr", raw.toString().trim());
        if (matchedPages < pages.size()) warnings.add((pages.size() - matchedPages) + " photo(s) did not contain " + targetId + " and were ignored.");
        warnings.add("Values were selected across all photos by completeness and field format. Review highlighted fields before generating.");
        return new Result(out, warnings);
    }

    private static Cell findIdCell(Page page, String target) {
        String wanted = target.replace(" ", "").toUpperCase(Locale.US);
        Cell best = null;
        for (Cell cell : page.cells) {
            Matcher matcher = ID.matcher(cell.text);
            if (matcher.find() && ("V" + matcher.group(1)).equals(wanted)) {
                if (best == null || (!cell.line && best.line)) best = cell;
            }
        }
        return best;
    }

    private static List<Header> findHeaders(Page page, float rowY) {
        Map<String, Header> found = new LinkedHashMap<>();
        for (Cell cell : page.cells) {
            if (cell.y >= rowY || !cell.line) continue;
            String normalized = normalize(cell.text);
            for (Map.Entry<String, List<String>> field : HEADERS.entrySet()) {
                for (String alias : field.getValue()) {
                    boolean match = normalized.equals(alias) || (!alias.equals("time") && normalized.contains(alias));
                    if (!match) continue;
                    Header old = found.get(field.getKey());
                    if (old == null || cell.y > old.cell.y || (cell.y == old.cell.y && cell.text.length() > old.cell.text.length()))
                        found.put(field.getKey(), new Header(field.getKey(), cell));
                    break;
                }
            }
        }
        List<Header> headers = new ArrayList<>(found.values());
        headers.sort(Comparator.comparingDouble(h -> h.cell.x));
        return headers;
    }

    private static void setColumnBounds(List<Header> headers, float pageWidth) {
        for (int i = 0; i < headers.size(); i++) {
            Header current = headers.get(i);
            int previous = i - 1;
            while (previous >= 0 && Math.abs(headers.get(previous).cell.x - current.cell.x) < 2f) previous--;
            int next = i + 1;
            while (next < headers.size() && Math.abs(headers.get(next).cell.x - current.cell.x) < 2f) next++;
            current.left = previous < 0 ? Math.max(0, current.cell.left - current.cell.height) : (headers.get(previous).cell.x + current.cell.x) / 2f;
            current.right = next >= headers.size() ? pageWidth : (current.cell.x + headers.get(next).cell.x) / 2f;
        }
    }

    private static float estimateHeaderSlope(List<Header> headers) {
        if (headers.size() < 2) return 0;
        float meanX = 0, meanY = 0;
        for (Header h : headers) { meanX += h.cell.x; meanY += h.cell.y; }
        meanX /= headers.size(); meanY /= headers.size();
        float numerator = 0, denominator = 0;
        for (Header h : headers) {
            float dx = h.cell.x - meanX;
            numerator += dx * (h.cell.y - meanY);
            denominator += dx * dx;
        }
        if (denominator == 0) return 0;
        return Math.max(-0.20f, Math.min(0.20f, numerator / denominator));
    }

    private static float estimateRowHeight(Page page, Cell id) {
        List<Float> distances = new ArrayList<>();
        for (Cell cell : page.cells) {
            if (cell == id || !cell.line) continue;
            Matcher m = ID.matcher(cell.text);
            if (m.find()) distances.add(Math.abs(cell.y - id.y));
        }
        Collections.sort(distances);
        float spacing = distances.isEmpty() ? id.height * 2.4f : distances.get(0);
        return Math.max(id.height * 1.45f, Math.min(id.height * 3.0f, spacing * 0.46f));
    }

    private static String readBoundedCell(Page page, Header header, Cell id, float slope, float rowHeight, List<Header> headers) {
        List<Cell> pieces = new ArrayList<>();
        for (Cell cell : page.cells) {
            if (cell.line || cell == id || isDetectionId(cell.text)) continue;
            if (cell.x < header.left || cell.x >= header.right) continue;
            float expectedY = id.y + slope * (cell.x - id.x);
            if (Math.abs(cell.y - expectedY) <= rowHeight) pieces.add(cell);
        }
        pieces.sort((a, b) -> {
            int byY = Float.compare(a.y, b.y);
            return Math.abs(a.y - b.y) < Math.max(a.height, b.height) * .55f ? Float.compare(a.x, b.x) : byY;
        });
        StringBuilder joined = new StringBuilder();
        for (Cell piece : pieces) {
            if (joined.length() > 0) joined.append(' ');
            joined.append(piece.text);
        }
        return cleanCandidate(header.key, joined.toString());
    }

    private static void offer(Map<String, Candidate> best, String key, String rawValue) {
        String value = cleanCandidate(key, rawValue);
        if (value.isEmpty() || value.equalsIgnoreCase("N/A") || value.equals("...")) return;
        int score = score(key, value);
        Candidate old = best.get(key);
        if (old == null || score > old.score) best.put(key, new Candidate(value, score));
    }

    private static int score(String key, String value) {
        int score = Math.min(40, value.length());
        if (!value.contains("...")) score += 20;
        if (key.toLowerCase(Locale.US).contains("time") && value.matches(".*\\b\\d{2}:\\d{2}\\b.*")) score += 50;
        if (key.toLowerCase(Locale.US).contains("distance") && value.matches(".*\\d+.*")) score += 35;
        if (key.toLowerCase(Locale.US).contains("bearing") && value.matches(".*(?:\\d{1,3}|\\d{1,2}:\\d{2}).*")) score += 35;
        if (key.equals("bestCount") && value.matches("\\d{1,3}")) score += 50;
        if (key.equals("species") && value.matches(".*[A-Za-z]{4,}.*")) score += 40;
        return score;
    }

    private static String cleanCandidate(String key, String value) {
        String cleaned = value.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("(?i)^(null|none shown)\\s*$", "");
        if (key.toLowerCase(Locale.US).contains("time")) cleaned = time(cleaned);
        if (key.toLowerCase(Locale.US).contains("distance")) cleaned = cleaned.replaceAll("(?i)\\s*m(?:eters?)?\\s*$", "").trim();
        return cleaned;
    }

    private static void assign(DetectionRecord record, String key, String value) {
        String cleaned = cleanCandidate(key, value).replace('°', ' ').replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return;
        if (key.equals("mitigationResponse") && cleaned.equalsIgnoreCase("Engine Neutral")) cleaned = "the engine was shifted to neutral";
        if (key.equals("mitigationRequest") && cleaned.equalsIgnoreCase("Engine Neutral")) cleaned = "engine neutral";
        record.set(key, cleaned);
    }

    private static void enrichFromTargetRow(Map<String, Candidate> best, Page page, Cell id, float slope, float rowHeight) {
        List<Cell> rowCells = new ArrayList<>();
        for (Cell cell : page.cells) {
            if (!cell.line) continue;
            float expectedY = id.y + slope * (cell.x - id.x);
            if (Math.abs(cell.y - expectedY) <= rowHeight) rowCells.add(cell);
        }
        rowCells.sort(Comparator.comparingDouble(c -> c.x));
        StringBuilder row = new StringBuilder();
        for (Cell cell : rowCells) row.append(' ').append(cell.text);
        String lower = row.toString().toLowerCase(Locale.US);
        String[] species = {"short-beaked common dolphin", "common dolphin", "minke whale", "fin whale", "humpback whale", "right whale", "unidentified mysticete whale", "unidentified mysticete", "harbor porpoise"};
        for (String candidate : species) if (lower.contains(candidate)) offer(best, "species", Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1));
        if (lower.contains("voluntary approach")) offerAppend(best, "behaviors", "voluntarily approaching the vessel");
        if (lower.contains("bow rid")) offerAppend(best, "behaviors", "bow riding");
        if (lower.contains("porpois")) offerAppend(best, "behaviors", "porpoising");
        if (lower.contains("feeding")) offerAppend(best, "behaviors", "feeding");
        if (lower.contains("travel")) offerAppend(best, "behaviors", "traveling");
        if (lower.contains("did not enter") && lower.contains("separation")) offer(best, "separationOutcome", "did not enter the separation distance");
        else if (lower.contains("remain") && lower.contains("separation")) offer(best, "separationOutcome", "remained within the separation distance");
        else if (lower.contains("enter") && lower.contains("separation")) offer(best, "separationOutcome", "entered the separation distance");
        if (lower.contains("no active piling")) offer(best, "pilingStatus", "No piling mitigation was needed as there was no active piling during the detection");
        if (lower.contains("detection delay")) offerAppend(best, "pilingStatus", "piling was under a detection delay");
    }

    private static void offerAppend(Map<String, Candidate> best, String key, String value) {
        Candidate old = best.get(key);
        if (old == null) { offer(best, key, value); return; }
        if (!old.value.toLowerCase(Locale.US).contains(value.toLowerCase(Locale.US))) offer(best, key, old.value + ", and " + value);
    }

    private static boolean isDetectionId(String text) { return ID.matcher(text).find(); }
    private static String normalize(String value) { return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }
    private static String time(String value) {
        Matcher matcher = Pattern.compile("(?:20\\d{2}-\\d{2}-\\d{2}[ T])?(\\d{2}:\\d{2})(?::\\d{2}(?:\\.\\d+)?)?", Pattern.CASE_INSENSITIVE).matcher(value);
        return matcher.find() ? matcher.group(1) : value;
    }
}
