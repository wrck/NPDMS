package com.dp.deviceops.parser.semantic.input;

import com.dp.deviceops.parser.semantic.DefaultDynamicSemanticParser;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DeviceLogInputNormalizerTest {
    private final DeviceLogInputNormalizer normalizer = new DeviceLogInputNormalizer();

    @Test void parsesConsoleConfigurationWithoutCommandEcho() throws Exception {
        var input = normalizer.normalize("!\ninterface Ethernet1\n description uplink\n ip address 192.0.2.1 255.255.255.0\n!\n", null);
        var bundle = new ParserReleaseBundleCodec().decode(
                Path.of("..", "parser-releases", "device-command-output-1.3.0"));
        var plan = new ParserPlanCompiler().compile(bundle);
        byte[] payload = new ObjectMapper().writeValueAsBytes(input.input());
        var result = new DefaultDynamicSemanticParser().parse(plan, () -> new ByteArrayInputStream(payload));
        assertFalse(result.genericContent().units().isEmpty());
        assertEquals("", result.genericContent().units().getFirst().commandText());
        assertEquals(1, result.genericContent().units().getFirst().sourceLineStart());
        assertTrue(input.warnings().contains("COMMAND_NOT_IDENTIFIED"));
    }

    @Test void separatesPromptCommandsAndPreservesBlankEvidenceLines() {
        var result = normalizer.normalize("sw#show version\r\n\r\nVersion 1\r\n<sw>display interface\r\nUp\r\n", "show run");
        var blocks = result.input().commandBlocks();
        assertEquals(2, blocks.size());
        assertEquals("show version", blocks.getFirst().commandText());
        assertEquals("\nVersion 1", blocks.getFirst().stdout());
        assertEquals("display interface", blocks.get(1).commandText());
        assertEquals("Up\n", blocks.get(1).stdout());
        assertTrue(result.warnings().contains("UPLOADED_LOG_EXECUTION_NOT_VERIFIED"));
    }

    @Test void supportsHintAndKeepsUnknownOutputAsEvidence() {
        assertEquals("show run", normalizer.normalize("hostname sample", "show run").input().commandBlocks().getFirst().commandText());
        var unknown = normalizer.normalize("unrecognized diagnostic output", null);
        assertTrue(unknown.warnings().contains("COMMAND_NOT_IDENTIFIED"));
        assertEquals("unrecognized diagnostic output", unknown.input().commandBlocks().getFirst().stdout());
    }

    @Test void echoedCommandDoesNotCreateAnEmptyDuplicateBlock() {
        var result = normalizer.normalize("sw#show version\nshow version\nVersion 1\nsw#show run\nhostname sample", null);
        assertEquals(2, result.input().commandBlocks().size());
        assertEquals("Version 1", result.input().commandBlocks().getFirst().stdout());
    }

    @Test void removesPasswordsTokensAndMultilinePrivateKeysBeforePersistence() {
        var result = normalizer.normalize("sw#show run\npassword clear-secret\naccess_token=token-secret\nAuthorization: Bearer auth-secret\n-----BEGIN PRIVATE KEY-----\nkey-secret\n-----END PRIVATE KEY-----\nhostname safe", null);
        var text = result.input().commandBlocks().getFirst().stdout();
        for (String secret : new String[]{"clear-secret", "token-secret", "auth-secret", "key-secret"}) assertFalse(text.contains(secret));
        assertTrue(text.contains("hostname safe"));
        assertTrue(result.warnings().contains("SENSITIVE_VALUES_REDACTED"));
    }

    @Test void rejectsEmptyBinaryMalformedJsonAndExcessiveBlocks() {
        for (String input : new String[]{" ", "a\0b", "{broken", "{\"schemaVersion\":\"1.0.0\",\"commandBlocks\":[null]}"}) {
            assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(input, null));
        }
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("sw#show run\noutput\n".repeat(1025), null));
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize("output", "show run\nshow version"));
    }

    @Test void redactsQuotedJsonSecretsInsideDeviceOutput() {
        var result = normalizer.normalize("{\"token\":\"json-token\",\"password\":\"json-password\",\"hostname\":\"safe\"}", "show status");
        String text = result.input().commandBlocks().getFirst().stdout();
        assertFalse(text.contains("json-token"));
        assertFalse(text.contains("json-password"));
        assertTrue(text.contains("safe"));
    }
}
