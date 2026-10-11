package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SlotDeviceReleaseTest {
    final CanonicalJson canonical=new CanonicalJson();
    final Path root=Path.of(System.getProperty("basedir"),"..","parser-releases","device-command-output-1.3.2");
    @Test void allFourReviewedFixturesRemainByteStable() throws Exception {
        var bundle=new ParserReleaseBundleCodec().decode(root);var plan=new ParserPlanCompiler().compile(bundle);
        for (var fixture:bundle.verificationCases()) {
            var result=new DefaultDynamicSemanticParser().parse(plan,()->new ByteArrayInputStream(fixture.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertArrayEquals(fixture.expectedResultJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),canonical.bytes(result),fixture.caseId());
        }
    }
    @Test void slotTableContainsIdentityAndOwnHardwareWithoutInventingChildSoftware() throws Exception {
        var result=parseSlot();var basic=(Map<?,?>)result.projections().get("deviceBasic");
        var slots=(List<?>)basic.get("slotDevices");assertEquals(2,slots.size());
        var first=(Map<?,?>)slots.getFirst();assertEquals("SN-CHILD-4",first.get("serialNumber"));
        assertEquals("A",first.get("pcbVersion"));assertEquals("1.00",first.get("cpldVersion"));assertNull(first.get("softVersion"));
    }
    @Test void abbreviatedVersionCommandRetainsFourParentVersions() throws Exception {
        var input=canonical.mapper().readTree(root.resolve("input-generic-version.json").toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode)input.get("commandBlocks").get(0)).put("commandText","show ver");
        var basic=(Map<?,?>)parse(input).projections().get("deviceBasic");
        assertEquals("SOFT-TEST",basic.get("softVersion"));assertEquals("15.02.03",basic.get("basicBootloaderVersion"));
        assertEquals("B",basic.get("pcbVersion"));assertEquals("4.00",basic.get("cpldVersion"));
    }
    SemanticParseResult parseSlot() throws Exception {return parse(canonical.mapper().readTree(root.resolve("input-slot-devices.json").toFile()));}
    SemanticParseResult parse(Object input) throws Exception {
        var bytes=canonical.bytes(input);var bundle=new ParserReleaseBundleCodec().decode(root);
        return new DefaultDynamicSemanticParser().parse(new ParserPlanCompiler().compile(bundle),()->new ByteArrayInputStream(bytes));
    }
}
