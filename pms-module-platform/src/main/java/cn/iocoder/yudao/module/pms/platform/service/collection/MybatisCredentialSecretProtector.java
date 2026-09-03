package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class MybatisCredentialSecretProtector implements CredentialSecretProtector {

    private static final String ENCRYPTOR_PROPERTY_NAME = "mybatis-plus.encryptor.password";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String protect(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("凭证秘密不能为空");
        }
        byte[] plaintext = encode(secret);
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] payload = ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array();
            try {
                return Base64.getEncoder().encodeToString(payload);
            } finally {
                Arrays.fill(ciphertext, (byte) 0);
                Arrays.fill(payload, (byte) 0);
            }
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("DEVICE_CREDENTIAL_ENCRYPTION_FAILED", ex);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    @Override
    public char[] reveal(String protectedSecret) {
        if (protectedSecret == null || protectedSecret.isBlank()) {
            throw new IllegalArgumentException("凭证密文不能为空");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(protectedSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("DEVICE_CREDENTIAL_CIPHERTEXT_INVALID", ex);
        }
        if (payload.length <= NONCE_LENGTH) {
            Arrays.fill(payload, (byte) 0);
            throw new IllegalStateException("DEVICE_CREDENTIAL_CIPHERTEXT_INVALID");
        }
        byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] plaintext = cipher.doFinal(ciphertext);
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
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("DEVICE_CREDENTIAL_DECRYPTION_FAILED", ex);
        } finally {
            Arrays.fill(payload, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    private static SecretKeySpec key() {
        String password = SpringUtil.getProperty(ENCRYPTOR_PROPERTY_NAME);
        if (password == null || password.isBlank() || "disabled".equals(password)) {
            throw new IllegalStateException("DEVICE_CREDENTIAL_ENCRYPTION_KEY_UNAVAILABLE");
        }
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        try {
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(passwordBytes), "AES");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
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
}
