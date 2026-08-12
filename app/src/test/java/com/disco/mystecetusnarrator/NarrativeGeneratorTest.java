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
        assertTrue(output.endsWith("during the detection."));
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
