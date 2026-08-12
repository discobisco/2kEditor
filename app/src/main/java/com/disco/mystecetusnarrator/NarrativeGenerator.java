package com.disco.mystecetusnarrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NarrativeGenerator {
    private NarrativeGenerator() {}
    public static List<String> missingRequired(DetectionRecord r) {
        String[] required={"initialTime","species","initialDistance","initialBearing","initialHeading","finalTime","finalBearing","finalHeading"};
        List<String> missing=new ArrayList<>(); for(String key:required)if(blank(r.get(key)))missing.add(DetectionRecord.LABELS.get(key)); return missing;
    }
    public static String generate(DetectionRecord r) {
        boolean one=singular(r.get("bestCount")); boolean cpaAtFinal=sameMoment(r.get("cpaTime"),r.get("finalTime")); StringBuilder n=new StringBuilder("At ").append(utc(required(r,"initialTime"))).append(", ");
        if(!blank(r.get("bestCount"))&&!one)n.append("a ").append(groupWord(r.get("species"))).append(" of ").append(r.get("bestCount")).append(' '); else n.append(blank(r.get("species"))?"a":articleFor(r.get("species"))).append(' ');
        n.append(speciesForCount(required(r,"species"),r.get("bestCount"))).append(" was detected ").append(distance(required(r,"initialDistance"))).append(' ').append(relative(r.get("initialPosition"))).append("at a bearing of ").append(clock(required(r,"initialBearing"))).append(" with a heading of ").append(clock(required(r,"initialHeading")));
        if(!blank(r.get("pathRelation")))n.append(", ").append(lowerFirst(r.get("pathRelation"))); n.append(". ");
        if(!blank(r.get("behaviors")))n.append("The ").append(one?"animal was":"animals were").append(" observed ").append(behaviorPhrase(r.get("behaviors"))).append(". ");
        if(!cpaAtFinal&&(!blank(r.get("cpaTime"))||!blank(r.get("cpaDistance"))||!blank(r.get("cpaBearing")))){n.append("The closest point of approach to the Rana Miller was");if(!blank(r.get("cpaTime")))n.append(" at ").append(utc(r.get("cpaTime")));if(!blank(r.get("cpaBearing")))n.append(" at the ").append(stripDegrees(r.get("cpaBearing"))).append(" o'clock position");if(!blank(r.get("cpaDistance")))n.append(", ").append(distance(r.get("cpaDistance")));if(!blank(r.get("cpaPosition")))n.append(' ').append(relative(r.get("cpaPosition")));if(!blank(r.get("cpaVesselHeading")))n.append(" relative to the vessel's heading of ").append(stripDegrees(r.get("cpaVesselHeading"))).append(" degrees");n.append(". ");}
        if(cpaAtFinal){n.append("Closest point of approach to the Rana Miller was at ").append(utc(required(r,"finalTime"))).append(", during final detection, ");if(!blank(r.get("finalDistance")))n.append(distance(r.get("finalDistance"))).append(' ');if(!blank(r.get("finalPosition")))n.append(relative(r.get("finalPosition")));n.append("at a bearing of ").append(clock(required(r,"finalBearing"))).append(" with a heading of ").append(clock(required(r,"finalHeading"))).append(". ");if(noEntry(r.get("separationOutcome"))&&!active(r.get("mitigationRequest")))n.append("No vessel strike avoidance was requested because the ").append(one?"animal":"animals").append(" did not enter the separation distance. ");else if(!blank(r.get("separationOutcome")))n.append(separationSentence(r.get("separationOutcome"),one)).append(' ');}
        else {if(!blank(r.get("separationOutcome")))n.append(separationSentence(r.get("separationOutcome"),one)).append(' ');n.append("The final detection was at ").append(utc(required(r,"finalTime"))).append(' ');if(!blank(r.get("finalDistance")))n.append(distance(r.get("finalDistance"))).append(' ').append(relative(r.get("finalPosition")));else if(!blank(r.get("finalPosition")))n.append(relative(r.get("finalPosition")));n.append("with a bearing of ").append(clock(required(r,"finalBearing"))).append(" and a heading of ").append(clock(required(r,"finalHeading"))).append(". ");}
        String request=r.get("mitigationRequest"),response=r.get("mitigationResponse");if(active(request)){n.append(mitigationName(request)).append(" mitigation was requested");if(!blank(r.get("requestTime")))n.append(" at ").append(utc(r.get("requestTime")));if(active(response)){n.append(" and ").append(responsePhrase(response));if(!blank(r.get("responseTime")))n.append(" at ").append(utc(r.get("responseTime")));}n.append(". ");}else if(active(response)){String p=responsePhrase(response);n.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));if(!blank(r.get("responseTime")))n.append(" at ").append(utc(r.get("responseTime")));n.append(". ");}
        if(!blank(r.get("pilingStatus")))n.append(pilingSentence(r.get("pilingStatus")));return n.toString().replaceAll("\\s+"," ").trim();
    }
    private static String groupWord(String s){s=s.toLowerCase(Locale.US);return s.contains("dolphin")||s.contains("porpoise")?"pod":"group";}
    private static String speciesForCount(String s,String c){if(blank(c)||"1".equals(c)||s.toLowerCase(Locale.US).endsWith("s")||s.startsWith("[MISSING:"))return s;return s+"s";}
    private static boolean singular(String c){return blank(c)||"1".equals(c.trim());}
    private static String required(DetectionRecord r,String k){String v=r.get(k);return blank(v)?"[MISSING: "+DetectionRecord.LABELS.get(k)+"]":v;}
    private static String articleFor(String s){return "aeiou".indexOf(Character.toLowerCase(s.charAt(0)))>=0?"an":"a";}
    private static String distance(String s){return s.startsWith("[MISSING:")?s:(s.toLowerCase(Locale.US).contains("meter")?s:s+" meters");}
    private static String relative(String s){return blank(s)?"":(s.toLowerCase(Locale.US).startsWith("off ")?s+" ":"off the "+s+" ");}
    private static String utc(String s){return s.startsWith("[MISSING:")?s:(s.toUpperCase(Locale.US).contains("UTC")?s:s+" UTC");}
    private static String clock(String s){return s.startsWith("[MISSING:")?s:(s.toLowerCase(Locale.US).contains("o'clock")?s:s+" o'clock");}
    private static String stripDegrees(String s){return s.replace("°","").trim();}
    private static String behaviorPhrase(String s){return lowerFirst(s.trim()).replaceAll("(?i)^observed\\s+","").replaceAll("(?i)voluntary approach","voluntarily approaching the vessel").replaceAll("(?i)bow[- ]?riding","bow riding").replaceAll("\\s+"," ");}
    private static String separationSentence(String v,boolean one){String s=v.toLowerCase(Locale.US),subject=one?"The animal":"The animals";if(s.contains("did not enter")||s.contains("outside"))return subject+" did not enter the separation distance.";if(s.contains("remain")&&s.contains("inside"))return subject+" remained within the separation distance.";if(s.contains("enter")||s.contains("inside"))return subject+" entered the separation distance.";if(s.contains("exit")||s.contains("left"))return subject+" exited the separation distance.";return ensureSentence(v);}
    private static boolean noEntry(String s){String l=s.toLowerCase(Locale.US);return l.contains("did not enter")||l.contains("outside");}
    private static boolean sameMoment(String a,String b){if(blank(a)||blank(b))return false;return a.toUpperCase(Locale.US).replace("UTC","").trim().equals(b.toUpperCase(Locale.US).replace("UTC","").trim());}
    private static boolean active(String s){return !blank(s)&&!s.equalsIgnoreCase("none")&&!s.equalsIgnoreCase("n/a");}
    private static String mitigationName(String s){String l=s.toLowerCase(Locale.US);if(l.contains("shutdown"))return "Shutdown";if(l.contains("vsa")||l.contains("vessel strike avoidance"))return "VSA";if(l.contains("neutral"))return "Engine-neutral";return s;}
    private static String responsePhrase(String s){String l=s.toLowerCase(Locale.US);if(l.contains("neutral"))return "the engine was shifted to neutral";if(l.contains("shutdown")||l.contains("shut down"))return "piling was shut down";if(l.contains("slow"))return "the vessel reduced speed";return lowerFirst(s);}
    private static String pilingSentence(String s){String l=s.toLowerCase(Locale.US);if(l.contains("no active piling")&&l.contains("delay"))return "No piling mitigation was needed because there was no active piling during the detection and piling was under a detection delay.";if(l.contains("no active piling"))return "No active piling during the detection, no mitigation required or requested.";if(l.contains("detection delay"))return "Piling was under a detection delay during the detection.";if(l.contains("active piling"))return "Active piling was occurring during the detection.";return ensureSentence(s);}
    private static String ensureSentence(String s){String t=Character.toUpperCase(s.charAt(0))+s.substring(1);return t.endsWith(".")?t:t+".";}
    private static String lowerFirst(String s){return Character.toLowerCase(s.charAt(0))+s.substring(1);}
    private static boolean blank(String s){return s==null||s.trim().isEmpty();}
}
