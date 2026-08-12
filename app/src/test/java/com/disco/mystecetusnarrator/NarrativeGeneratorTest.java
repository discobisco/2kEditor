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
        assertTrue(output.endsWith("requested."));
    }

    @Test public void identifiesMissingRequiredFields() {
        assertEquals(9, NarrativeGenerator.missingRequired(new DetectionRecord()).size());
    }
}
