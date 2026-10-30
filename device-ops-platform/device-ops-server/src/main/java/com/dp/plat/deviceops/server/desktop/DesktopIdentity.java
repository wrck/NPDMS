package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.AesGcmCredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** An additive identity file; never replaces the credential key, H2 database or outbox. */
@Component
@Profile("desktop")
public final class DesktopIdentity {
    private final KeyPair key;
    private final String instanceId = UUID.randomUUID().toString();

    @org.springframework.beans.factory.annotation.Autowired
    public DesktopIdentity(@Value("${device-ops.credentials.master-key}") String masterKey,
            @Value("${device-ops.desktop.identity-file:desktop-identity.properties}") String file) throws Exception {
        this(Path.of(file), new AesGcmCredentialCipher(masterKey));
    }

    DesktopIdentity(Path file, AesGcmCredentialCipher cipher) throws Exception {
        var stored = new Properties();
        if (Files.exists(file)) {
            try (var input = Files.newInputStream(file)) { stored.load(input); }
        } else {
            var generated = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            char[] privateText = Base64.getEncoder().encodeToString(generated.getPrivate().getEncoded()).toCharArray();
            try {
                var encrypted = cipher.encrypt(privateText);
                stored.setProperty("private", Base64.getEncoder().encodeToString(encrypted.ciphertext()));
                stored.setProperty("nonce", Base64.getEncoder().encodeToString(encrypted.nonce()));
                stored.setProperty("public", Base64.getEncoder().encodeToString(generated.getPublic().getEncoded()));
                // Create once. Corrupt/existing identity is an error, never silently re-pair.
                try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) { stored.store(output, "DAC encrypted installation identity"); }
            } finally { Arrays.fill(privateText, '\0'); }
        }
        char[] privateText = cipher.decrypt(Base64.getDecoder().decode(stored.getProperty("private")),
                Base64.getDecoder().decode(stored.getProperty("nonce")), "v1");
        byte[] privateBytes = null;
        try {
            privateBytes = Base64.getDecoder().decode(new String(privateText));
            var factory = KeyFactory.getInstance("Ed25519");
            key = new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(stored.getProperty("public")))),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)));
            // Detect mismatched keys rather than publishing an unusable identity.
            var verifier = Signature.getInstance("Ed25519"); verifier.initVerify(key.getPublic());
            verifier.update("identity-check".getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(sign("identity-check"))) throw new GeneralSecurityException("identity differs");
        } finally {
            Arrays.fill(privateText, '\0');
            if (privateBytes != null) Arrays.fill(privateBytes, (byte) 0);
        }
    }

    String publicKey() {
        byte[] encoded = key.getPublic().getEncoded();
        return HexFormat.of().formatHex(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
    }
    String instanceId() { return instanceId; }
    String pairingCode() throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(HexFormat.of().parseHex(publicKey())), 0, 16).toUpperCase(Locale.ROOT);
    }
    byte[] sign(String message) throws GeneralSecurityException {
        var signature = Signature.getInstance("Ed25519"); signature.initSign(key.getPrivate());
        signature.update(message.getBytes(StandardCharsets.UTF_8)); return signature.sign();
    }
}
