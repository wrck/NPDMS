package com.dp.plat.lowcode.engine.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.Set;

/**
 * 连接器凭据 AES-GCM 加密器（借鉴 Mendix 连接器凭据加密）。
 *
 * <p>加密算法：AES/GCM/NoPadding，密钥从配置 app.encrypt-key 读取（16/24/32 字节）。
 * 密文格式：Base64(IV(12B) + ciphertext + tag(16B))，前缀 ENC: 用于标识加密字段。</p>
 *
 * <p>相比 ECB 模式，GCM 提供机密性 + 完整性认证（AEAD），相同明文每次密文不同（IV 随机），
 * 是当前推荐的对称加密模式。</p>
 *
 * <p>识别的敏感字段名：password / token / apiKey / secret / clientSecret，
 * 通过 {@link #encryptConfig(String)} / {@link #decryptConfig(String)} 递归遍历 config JSON。</p>
 */
@Slf4j
@Component
public class ConnectorCredentialEncryptor {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final String PREFIX = "ENC:";

    /** 配置 JSON 中需加密的敏感字段名集合。 */
    static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "apiKey", "secret", "clientSecret");

    private final SecretKeySpec keySpec;
    private final ObjectMapper objectMapper;

    public ConnectorCredentialEncryptor(
            @Value("${app.encrypt-key:default-encrypt-key-32b}") String key,
            ObjectMapper objectMapper) {
        byte[] keyBytes = padKey(key.getBytes(StandardCharsets.UTF_8));
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        this.objectMapper = objectMapper;
    }

    /** 加密：明文 → ENC:Base64(IV+cipher+tag) */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        if (plaintext.startsWith(PREFIX)) return plaintext;  // 已加密，幂等
        try {
            byte[] iv = new byte[IV_LENGTH];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("凭据加密失败", e);
        }
    }

    /** 解密：ENC:Base64 → 明文；非 ENC: 前缀的值原样返回（兼容明文/历史数据） */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) return ciphertext;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherBytes = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("凭据解密失败", e);
        }
    }

    /** 判断字符串是否为 ENC: 加密标记形式。 */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.length() > PREFIX.length();
    }

    /**
     * 对连接器配置 JSON 中的敏感字段加密（递归遍历对象/数组）。
     *
     * @param configJson 配置 JSON 字符串
     * @return 加密后的配置 JSON 字符串
     */
    public String encryptConfig(String configJson) {
        return processConfig(configJson, true);
    }

    /**
     * 对连接器配置 JSON 中的敏感字段解密（递归遍历对象/数组）。
     *
     * @param configJson 配置 JSON 字符串
     * @return 解密后的配置 JSON 字符串
     */
    public String decryptConfig(String configJson) {
        return processConfig(configJson, false);
    }

    private String processConfig(String configJson, boolean encrypt) {
        if (configJson == null || configJson.isBlank()) {
            return configJson;
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            JsonNode processed = processNode(root, encrypt);
            return objectMapper.writeValueAsString(processed);
        } catch (Exception e) {
            log.warn("连接器配置 JSON 解析失败，跳过凭据{}处理", encrypt ? "加密" : "解密", e);
            return configJson;
        }
    }

    /**
     * 递归处理 JsonNode：对对象中的敏感字段值做加/解密，对子对象/数组递归。
     * 返回新节点（保留原结构）。
     */
    private JsonNode processNode(JsonNode node, boolean encrypt) {
        if (node.isObject()) {
            ObjectNode obj = ((ObjectNode) node).deepCopy();
            Iterator<String> fields = obj.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                JsonNode child = obj.get(field);
                if (child.isValueNode() && SENSITIVE_FIELDS.contains(field)) {
                    String value = child.asText("");
                    String processed = encrypt ? encrypt(value) : decrypt(value);
                    obj.put(field, processed);
                } else if (child.isContainerNode()) {
                    obj.set(field, processNode(child, encrypt));
                }
            }
            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = ((ArrayNode) node).deepCopy();
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                if (child.isContainerNode()) {
                    arr.set(i, processNode(child, encrypt));
                }
            }
            return arr;
        }
        return node;
    }

    /** 密钥填充到 16/24/32 字节 */
    private byte[] padKey(byte[] key) {
        if (key.length >= 32) return Arrays.copyOf(key, 32);
        if (key.length >= 24) return Arrays.copyOf(key, 24);
        return Arrays.copyOf(key, 16);
    }
}
