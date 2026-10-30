package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.AesGcmCredentialCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DesktopIdentityTest {
    @TempDir Path directory;
    @Test void preservesEncryptedIdentityAcrossRestartsWithoutChangingExistingData() throws Exception {
        var key = new byte[32]; Arrays.fill(key, (byte) 7);
        var cipher = new AesGcmCredentialCipher(Base64.getEncoder().encodeToString(key));
        var database = directory.resolve("device-ops.mv.db"); Files.writeString(database, "existing-connections-and-tasks");
        var file = directory.resolve("identity.properties");
        var first = new DesktopIdentity(file, cipher);
        byte[] identityBytes = Files.readAllBytes(file);
        var second = new DesktopIdentity(file, cipher);
        assertEquals(first.publicKey(), second.publicKey());
        assertEquals(first.pairingCode(), second.pairingCode());
        assertNotEquals(first.instanceId(), second.instanceId());
        assertArrayEquals(identityBytes, Files.readAllBytes(file));
        assertEquals("existing-connections-and-tasks", Files.readString(database));
        String message = "npdms-dac-discovery-v1\nnonce\n8289\n" + second.instanceId();
        byte[] spki = HexFormat.of().parseHex("302a300506032b6570032100" + first.publicKey());
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki)));
        verifier.update(message.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(second.sign(message)));
        var wrongKey = new byte[32]; Arrays.fill(wrongKey, (byte) 9);
        assertThrows(Exception.class, () -> new DesktopIdentity(file, new AesGcmCredentialCipher(Base64.getEncoder().encodeToString(wrongKey))));
        assertArrayEquals(identityBytes, Files.readAllBytes(file));
    }
    @Test void corruptIdentityIsNeverRegenerated() throws Exception {
        var file = directory.resolve("identity.properties"); Files.writeString(file, "corrupt");
        assertThrows(Exception.class, () -> new DesktopIdentity(file, new AesGcmCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]))));
        assertEquals("corrupt", Files.readString(file));
    }
}
