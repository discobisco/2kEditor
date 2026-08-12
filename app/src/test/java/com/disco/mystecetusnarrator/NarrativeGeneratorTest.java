package com.disco.mystecetusnarrator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NarrativeGeneratorTest {
    @Test public void generatesV339StyleNarrative() {
        DetectionRecord r = new DetectionRecord();
        r.set("initialTime", "03:44");
        r.set("species", "short-beaked common dolphins");
        r.set("bestCount", "6");
        r.set("initialDistance", "50");
        r.set("initialPosition", "port bow");
        r.set("initialBearing", "188 + 11:30");
        r.set("initialHeading", "188 + 1:00");
        r.set("behaviors", "bow riding and voluntarily approaching the vessel");
        r.set("cpaTime", "03:50");
        r.set("cpaDistance", "10");
        r.set("finalTime", "03:52");
        r.set("finalDistance", "20");
        r.set("finalPosition", "port bow");
        r.set("finalBearing", "188 + 11:30");
        r.set("finalHeading", "188 + 1:00");
        r.set("pilingStatus", "No active piling occurred during the detection; therefore, no mitigation was required or requested");
        String output = NarrativeGenerator.generate(r);
        assertTrue(output.startsWith("At 03:44 UTC, a pod of 6 short-beaked common dolphins was detected"));
        assertTrue(output.contains("The animals were observed bow riding"));
        assertTrue(output.endsWith("no mitigation required or requested."));
    }

    @Test public void generatesCpaDuringFinalDetectionReferenceFormat() {
        DetectionRecord r = new DetectionRecord();
        r.set("initialTime", "12:55");
        r.set("species", "short-beaked common dolphin");
        r.set("bestCount", "8");
        r.set("initialDistance", "688");
        r.set("initialPosition", "starboard bow");
        r.set("initialBearing", "241 + 01:00");
        r.set("initialHeading", "241 + 09:00");
        r.set("behaviors", "traveling with vigorous pace, porpoising, and feeding");
        r.set("cpaTime", "13:15");
        r.set("cpaDistance", "565");
        r.set("finalTime", "13:15");
        r.set("finalDistance", "565");
        r.set("finalPosition", "bow");
        r.set("finalBearing", "133 + 12:00");
        r.set("finalHeading", "133 + 09:30");
        r.set("separationOutcome", "did not enter the separation distance");
        r.set("pilingStatus", "No active piling during the detection");
        assertEquals("At 12:55 UTC, a pod of 8 short-beaked common dolphins was detected 688 meters off the starboard bow at a bearing of 241 + 01:00 o'clock with a heading of 241 + 09:00 o'clock. The animals were observed traveling with vigorous pace, porpoising, and feeding. Closest point of approach to the Rana Miller was at 13:15 UTC, during final detection, 565 meters off the bow at a bearing of 133 + 12:00 o'clock with a heading of 133 + 09:30 o'clock. No vessel strike avoidance was requested because the animals did not enter the separation distance. No active piling during the detection, no mitigation required or requested.", NarrativeGenerator.generate(r));
    }

    @Test public void identifiesMissingRequiredFields() {
        assertEquals(8, NarrativeGenerator.missingRequired(new DetectionRecord()).size());
    }

    @Test public void omitsUnavailableCpaAndFinalDistance() {
        DetectionRecord r = completeSingle();
        String output = NarrativeGenerator.generate(r);
        assertTrue(!output.contains("closest point of approach"));
        assertTrue(!output.contains("Final distance"));
    }

    @Test public void handlesMitigationPilingAndSeparation() {
        DetectionRecord r = completeSingle();
        r.set("separationOutcome", "did not enter the separation distance");
        r.set("mitigationRequest", "VSA"); r.set("requestTime", "12:24");
        r.set("mitigationResponse", "Engine Neutral"); r.set("responseTime", "12:24");
        r.set("pilingStatus", "No active piling; detection delay");
        String output = NarrativeGenerator.generate(r);
        assertTrue(output.contains("The animal did not enter the separation distance."));
        assertTrue(output.contains("VSA mitigation was requested at 12:24 UTC and the engine was shifted to neutral at 12:24 UTC."));
        assertTrue(output.contains("no active piling during the detection and piling was under a detection delay"));
    }

    private static DetectionRecord completeSingle() {
        DetectionRecord r = new DetectionRecord();
        r.set("initialTime", "12:03"); r.set("species", "minke whale"); r.set("bestCount", "1");
        r.set("initialDistance", "150"); r.set("initialBearing", "101 + 12:00"); r.set("initialHeading", "101 + 09:00");
        r.set("finalTime", "12:09"); r.set("finalBearing", "135 + 06:00"); r.set("finalHeading", "135 + 09:00");
        return r;
    }
}
