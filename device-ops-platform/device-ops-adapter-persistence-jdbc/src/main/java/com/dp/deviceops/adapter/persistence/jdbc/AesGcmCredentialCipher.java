package com.dp.deviceops.adapter.persistence.jdbc;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class AesGcmCredentialCipher {
    static final String KEY_VERSION = "v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCredentialCipher(String encodedMasterKey) {
        if (encodedMasterKey == null || encodedMasterKey.isBlank()) {
            key = null;
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedMasterKey.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("credential master key must be Base64 encoded", exception);
        }
        try {
            if (decoded.length != 32) {
                throw new IllegalArgumentException("credential master key must decode to 32 bytes");
            }
            key = new SecretKeySpec(Arrays.copyOf(decoded, decoded.length), "AES");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    public boolean available() {
        return key != null;
    }

    public EncryptedValue encrypt(char[] value) {
        requireAvailable();
        if (value == null || value.length == 0) {
            return null;
        }
        byte[] plaintext = encode(value);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new EncryptedValue(cipher.doFinal(plaintext), nonce);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("credential encryption failed", exception);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public char[] decrypt(byte[] ciphertext, byte[] nonce, String keyVersion) {
        requireAvailable();
        if (ciphertext == null) {
            return null;
        }
        if (!KEY_VERSION.equals(keyVersion)) {
            throw new IllegalStateException("unsupported credential key version");
        }
        byte[] plaintext;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            plaintext = cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("credential decryption failed", exception);
        }
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plaintext));
            char[] result = new char[decoded.remaining()];
            decoded.get(result);
            if (decoded.hasArray()) {
                Arrays.fill(decoded.array(), '\0');
            }
            return result;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private void requireAvailable() {
        if (!available()) {
            throw new com.dp.deviceops.core.port.CredentialStore.UnavailableException();
        }
    }

    private static byte[] encode(char[] value) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] result = new byte[encoded.remaining()];
        encoded.get(result);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return result;
    }

    public record EncryptedValue(byte[] ciphertext, byte[] nonce) {
        public EncryptedValue {
            ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
            nonce = Arrays.copyOf(nonce, nonce.length);
        }
    }
}
