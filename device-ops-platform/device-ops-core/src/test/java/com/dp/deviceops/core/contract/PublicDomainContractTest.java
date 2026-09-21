package com.dp.deviceops.core.contract;

import com.dp.deviceops.core.model.ScriptArtifact;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicDomainContractTest {

    @Test
    void exposesScriptSourcePersistencePolicyAndConflictExceptionToOtherPackages() {
        ScriptArtifact local = ScriptArtifact.local("local-check", "1", "show clock", sha256("show clock"),
                ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null);
        ScriptArtifact external = ScriptArtifact.external("external-check", "1", "show version", sha256("show version"),
                ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null);

        assertEquals(ScriptArtifact.ScriptSource.LOCAL_MANAGED, local.source());
        assertEquals(ScriptArtifact.ScriptSource.EXTERNAL_DELIVERED, external.source());
        assertThrows(ScriptArtifact.DomainConflictException.class, () -> external.assertSameVersion(
                ScriptArtifact.external("external-check", "1", "display clock", sha256("display clock"),
                        ScriptArtifact.PersistencePolicy.REGISTER_VERSION, "NONE", null)));
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
