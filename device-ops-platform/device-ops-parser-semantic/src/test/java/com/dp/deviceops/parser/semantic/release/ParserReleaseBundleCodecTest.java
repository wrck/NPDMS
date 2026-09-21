package com.dp.deviceops.parser.semantic.release;

import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.DefaultDynamicSemanticParser;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserReleaseBundleCodecTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void decodesAndCompilesAnExactReleaseDirectory() throws Exception {
        ParserReleaseBundle bundle = new ParserReleaseBundleCodec().decode(release("device-show-tech-1.0.0"));
        ParserPlan plan = new ParserPlanCompiler().compile(bundle);

        assertEquals("device-show-tech", plan.coordinate().logType());
        assertEquals("1.0.0", plan.coordinate().releaseVersion());
        assertEquals("command-output-block/v1", plan.inputFormat());
        assertEquals(1, bundle.verificationCases().size());
        assertEquals("device-version", bundle.verificationCases().getFirst().caseId());
    }

    @Test
    void rejectsVerificationFilesOutsideTheReleaseDirectory() throws Exception {
        Path release = temporaryDirectory.resolve("release");
        Files.createDirectories(release);
        Files.writeString(release.resolve("manifest.json"), """
                {"manifestVersion":"1.0.0","logType":"line-log","releaseVersion":"1.0.0",
                "inputAdapter":"line-log/v1","inputSchemaVersion":"1.0.0","outputSchemaVersion":"1.0.0",
                "engineVersion":"1.0.0","ruleVersion":"1.0.0","projectionVersion":"1.0.0"}
                """, StandardCharsets.UTF_8);
        Files.writeString(release.resolve("rules.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(release.resolve("projections.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(release.resolve("verification-cases.json"), """
                {"cases":[{"caseId":"escape","inputFile":"../outside.log","expectedFile":"expected.json"}]}
                """, StandardCharsets.UTF_8);

        SemanticParserError error = assertThrows(SemanticParserError.class,
                () -> new ParserReleaseBundleCodec().decode(release));

        assertEquals(SemanticParserError.INVALID_RULES, error.code());
    }

    @Test
    void decodesCompilesAndPersistsModelAwareRuleArtifacts() throws Exception {
        Path release = modelAwareRelease();

        ParserReleaseBundle bundle = new ParserReleaseBundleCodec().decode(release);
        ParserPlan plan = new ParserPlanCompiler().compile(bundle);
        String stored = new ParserRuleArtifactCodec().encode(bundle);
        ParserRuleArtifactCodec.Decoded decoded = new ParserRuleArtifactCodec()
                .decode(bundle.manifest(), stored);

        assertNull(bundle.rulesJson());
        assertEquals(2, bundle.ruleSetJsonByPath().size());
        assertEquals(List.of("rules/base.json", "rules/vpn.json"),
                List.copyOf(bundle.ruleSetJsonByPath().keySet()));
        assertEquals(1, plan.discoveryRules().size());
        assertEquals("generic", plan.genericProfile().profileId());
        assertEquals("vpn-family", plan.modelProfiles().getFirst().profileId());
        assertEquals(bundle.modelProfilesJson(), decoded.modelProfilesJson());
        assertEquals(bundle.ruleSetJsonByPath(), decoded.ruleSetJsonByPath());
        assertEquals(List.copyOf(bundle.ruleSetJsonByPath().keySet()),
                List.copyOf(decoded.ruleSetJsonByPath().keySet()));
    }

    @Test
    void rejectsOverlappingModelMatchesBeforeCompilation() {
        assertThrows(IllegalArgumentException.class, () -> new ModelProfileCatalog(
                "1.0.0", List.of("rules/base.json"),
                new ModelProfileCatalog.Profile("generic", null, List.of()),
                List.of(
                        new ModelProfileCatalog.Profile("family", new ModelProfileCatalog.Match(
                                List.of(), List.of("VPN1000-")), List.of()),
                        new ModelProfileCatalog.Profile("exact", new ModelProfileCatalog.Match(
                                List.of("VPN1000-GA-X"), List.of()), List.of()))));
    }

    @Test
    void everyBundledVerificationCaseProducesItsFrozenResult() throws Exception {
        for (String releaseName : new String[]{"device-show-tech-1.0.0", "line-log-1.0.0"}) {
            ParserReleaseBundle bundle = new ParserReleaseBundleCodec().decode(release(releaseName));
            ParserPlan plan = new ParserPlanCompiler().compile(bundle);
            for (ParserReleaseBundle.VerificationCase verificationCase : bundle.verificationCases()) {
                byte[] actual = new CanonicalJson().bytes(new DefaultDynamicSemanticParser().parse(plan,
                        () -> new ByteArrayInputStream(verificationCase.inputContent()
                                .getBytes(StandardCharsets.UTF_8))));
                assertArrayEquals(verificationCase.expectedResultJson()
                        .getBytes(StandardCharsets.UTF_8), actual, verificationCase.caseId());
            }
        }
    }

    private static Path release(String name) throws Exception {
        return Path.of(ParserReleaseBundleCodecTest.class.getResource("/releases/" + name).toURI());
    }

    private Path modelAwareRelease() throws Exception {
        Path release = temporaryDirectory.resolve("model-aware");
        Files.createDirectories(release.resolve("rules"));
        Files.writeString(release.resolve("manifest.json"), """
                {"manifestVersion":"1.0.0","logType":"device-command-output","releaseVersion":"1.1.0",
                "inputAdapter":"command-output-block/v1","inputSchemaVersion":"1.0.0","outputSchemaVersion":"1.0.0",
                "engineVersion":"1.1.0","ruleVersion":"1.1.0","projectionVersion":"1.0.0"}
                """);
        Files.writeString(release.resolve("model-profiles.json"), """
                {"schemaVersion":"1.0.0","baseRuleSets":["rules/base.json"],
                 "genericProfile":{"profileId":"generic","ruleSets":[]},
                 "profiles":[{"profileId":"vpn-family","match":{"exact":[],"prefixes":["VPN1000-"]},
                 "ruleSets":["rules/vpn.json"]}]}
                """);
        Files.writeString(release.resolve("rules/base.json"), """
                {"schemaVersion":"1.0.0","catalogVersion":"1.1.0","rules":[{
                 "ruleId":"model","blockRole":"DEVICE_VERSION",
                 "roleSelectors":[{"type":"COMMAND_REGEX","value":"^show version$"}],
                 "extractors":[{"type":"LINE_REGEX","pattern":"Model: (.+)","group":1,"transforms":["trim"]}],
                 "target":{"semanticKey":"device.identity.model","cardinality":"ONE","dataType":"string",
                 "conflictPolicy":"HIGHEST_CONFIDENCE"}}]}
                """);
        Files.writeString(release.resolve("rules/vpn.json"), """
                {"schemaVersion":"1.0.0","catalogVersion":"1.1.0","rules":[]}
                """);
        Files.writeString(release.resolve("projections.json"), """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","profiles":[{
                 "projectionId":"deviceBasic","missingValuePolicy":"OMIT",
                 "fields":{"model":"device.identity.model"}}]}
                """);
        Files.writeString(release.resolve("input.json"), """
                {"schemaVersion":"1.0.0","commandBlocks":[]}
                """);
        Files.writeString(release.resolve("expected.json"), "{}\n");
        Files.writeString(release.resolve("verification-cases.json"), """
                {"cases":[{"caseId":"model","inputFile":"input.json","expectedFile":"expected.json"}]}
                """);
        return release;
    }
}
