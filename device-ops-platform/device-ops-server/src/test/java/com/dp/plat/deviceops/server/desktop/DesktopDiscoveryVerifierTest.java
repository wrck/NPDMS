package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.AesGcmCredentialCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DesktopDiscoveryVerifierTest {
    @TempDir Path directory;
    @Test void nativeLauncherVerifiesPinnedIdentityAndRejectsNonceOrPortRelay() throws Exception {
        var identity = new DesktopIdentity(directory.resolve("identity"), new AesGcmCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])));
        String nonce = "a".repeat(64);
        var fields = new LinkedHashMap<String, Object>();
        fields.put("product", "sdp-dac"); fields.put("protocolVersion", 3); fields.put("status", "READY");
        fields.put("port", 8289); fields.put("publicKey", identity.publicKey()); fields.put("instanceId", identity.instanceId());
        fields.put("serverUrl", "http://npdms.example"); fields.put("webOrigin", "http://web.example");
        fields.put("signature", Base64.getEncoder().encodeToString(identity.sign("sdp-dac-discovery-v1\n" + nonce + "\n8289\n" + identity.instanceId()
                + "\n3\nREADY\nhttp://npdms.example\nhttp://web.example")));
        String payload = new ObjectMapper().writeValueAsString(fields);
        var expected = new DesktopDiscoveryVerifier.Expected(identity.publicKey(), nonce, 8289);
        assertTrue(DesktopDiscoveryVerifier.verify(expected, payload));
        assertFalse(DesktopDiscoveryVerifier.verify(new DesktopDiscoveryVerifier.Expected(identity.publicKey(), "b".repeat(64), 8289), payload));
        assertFalse(DesktopDiscoveryVerifier.verify(new DesktopDiscoveryVerifier.Expected(identity.publicKey(), nonce, 8280), payload));
        fields.put("serverUrl", "http://other.example");
        assertFalse(DesktopDiscoveryVerifier.verify(expected, new ObjectMapper().writeValueAsString(fields)));
        fields.put("port", 48182);
        assertFalse(DesktopDiscoveryVerifier.verify(new DesktopDiscoveryVerifier.Expected(identity.publicKey(), nonce, 48182), new ObjectMapper().writeValueAsString(fields)));
    }
}
