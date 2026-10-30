package com.dp.plat.deviceops.server.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Read-only native-launcher verifier. Runs without Spring, a service, a database or a private key. */
public final class DesktopDiscoveryVerifier {
    public record Expected(String publicKey, String nonce, int port) { }

    public static void main(String[] args) {
        try {
            if (args.length != 3 || !verify(new Expected(args[0], args[1], Integer.parseInt(args[2])),
                    new String(System.in.readNBytes(8193), StandardCharsets.UTF_8))) System.exit(1);
        } catch (Exception failure) { System.exit(1); }
    }

    static boolean verify(Expected expected, String payload) throws Exception {
        if (!expected.publicKey().matches("[a-f0-9]{64}") || !expected.nonce().matches("[a-f0-9]{64}")
                || expected.port() < 8280 || expected.port() > 8289 || payload.length() > 8192) return false;
        var data = new ObjectMapper().readTree(payload);
        if (!"npdms-dac".equals(data.path("product").asText()) || data.path("protocolVersion").asInt() != 3
                || !"READY".equals(data.path("status").asText()) || data.path("port").asInt() != expected.port()
                || !expected.publicKey().equals(data.path("publicKey").asText())) return false;
        String message = "npdms-dac-discovery-v1\n" + expected.nonce() + "\n" + expected.port() + "\n"
                + data.path("instanceId").asText() + "\n3\nREADY\n" + data.path("serverUrl").asText() + "\n" + data.path("webOrigin").asText();
        var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(
                HexFormat.of().parseHex("302a300506032b6570032100" + expected.publicKey())));
        var verifier = Signature.getInstance("Ed25519"); verifier.initVerify(key);
        verifier.update(message.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(data.path("signature").asText()));
    }
}
