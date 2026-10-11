package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GenericDeviceVersionReleaseTest {
    private final CanonicalJson canonical=new CanonicalJson();
    private final Path root=Path.of(System.getProperty("basedir"),"..","parser-releases","device-command-output-1.3.1");
    @Test void reviewedFixturesRemainByteStableIncludingExistingModelResults() throws Exception {
        var bundle=new ParserReleaseBundleCodec().decode(root);
        var plan=new ParserPlanCompiler().compile(bundle);
        for (var fixture:bundle.verificationCases()) {
            var result=new DefaultDynamicSemanticParser().parse(plan,()->new ByteArrayInputStream(fixture.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertArrayEquals(fixture.expectedResultJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),canonical.bytes(result),fixture.caseId());
        }
    }
    @Test void genericVersionOutputContainsFourVersionsAndUsesBasicRatherThanExtended() throws Exception {
        assertFour(parse("show version",false));
    }
    @Test void genericTechnicalOutputAlsoContainsFourVersions() throws Exception {
        assertFour(parse("show tech",false));
    }
    @Test void missingHardwareLinesDoNotInventOrUseExtendedBootVersion() throws Exception {
        var result=parse("show version",true);
        var basic=(Map<?,?>)result.projections().get("deviceBasic");
        assertEquals("SOFT-TEST",basic.get("softVersion"));
        assertNull(basic.get("cpldVersion"));assertNull(basic.get("pcbVersion"));assertNull(basic.get("basicBootloaderVersion"));
    }
    private SemanticParseResult parse(String command,boolean missing) throws Exception {
        var bundle=new ParserReleaseBundleCodec().decode(root);
        var input=canonical.mapper().readTree(root.resolve("input-generic-version.json").toFile());
        var block=(com.fasterxml.jackson.databind.node.ObjectNode)input.get("commandBlocks").get(0);
        block.put("commandText",command);
        if (missing) block.put("stdout",block.get("stdout").asText().replaceAll("(?m)^(?:PCB Version|CPLD Version|BASIC CONBOOT Version):[^\\r\\n]*\\n",""));
        byte[] bytes=canonical.bytes(input);
        return new DefaultDynamicSemanticParser().parse(new ParserPlanCompiler().compile(bundle),()->new ByteArrayInputStream(bytes));
    }
    private void assertFour(SemanticParseResult result) {
        assertEquals("generic",result.profileSelection().profileId());
        var basic=(Map<?,?>)result.projections().get("deviceBasic");
        assertEquals("SOFT-TEST",basic.get("softVersion"));assertEquals("4.00",basic.get("cpldVersion"));
        assertEquals("B",basic.get("pcbVersion"));assertEquals("15.02.03",basic.get("basicBootloaderVersion"));
    }
}
