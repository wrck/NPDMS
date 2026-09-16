package com.dp.plat.lowcode.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Iterator;
import java.util.Set;

/**
 * 凭据加解密工具。
 *
 * <p>用 AES（ECB 模式 + PKCS5Padding）对连接器配置中的敏感字段加密，
 * 密文以 {@code ENC(...)} 形式标记，便于区分明文/密文并保证幂等。
 * 密钥从配置 {@code lowcode.encryption.key} 读取（经 SHA-256 派生为 32 字节 AES-256 密钥）；
 * 使用默认密钥会在启动时打印警告日志。</p>
 *
 * <p>识别的敏感字段名：{@code password} / {@code credentials} / {@code token} /
 * {@code apiKey} / {@code secret} / {@code clientSecret}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialEncryptor {

    /** 默认密钥（与 application.yml 默认值一致）。 */
    static final String DEFAULT_KEY = "default-lowcode-encryption-key-change-me";

    static final String ENC_PREFIX = "ENC(";
    static final String ENC_SUFFIX = ")";

    /** 配置 JSON 中需加密的字段名集合。 */
    static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "credentials", "token", "apiKey", "secret", "clientSecret");

    @Value("${lowcode.encryption.key:" + DEFAULT_KEY + "}")
    private String encryptionKey;

    private final ObjectMapper objectMapper;

    private SecretKeySpec keySpec;

    @PostConstruct
    void init() {
        if (DEFAULT_KEY.equals(encryptionKey)) {
            log.warn("[lowcode] 当前使用默认连接器凭据加密密钥（lowcode.encryption.key），"
                    + "生产环境请通过环境变量 LOWCODE_ENCRYPTION_KEY 覆盖！");
        }
        keySpec = new SecretKeySpec(sha256(encryptionKey), "AES");
    }

    /**
     * 加密明文。若入参已为 {@code ENC(...)} 形式则原样返回（幂等）。
     *
     * @param plain 明文
     * @return 形如 {@code ENC(base64Cipher)} 的密文
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        if (isEncrypted(plain)) {
            return plain;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ENC_PREFIX + Base64.getEncoder().encodeToString(encrypted) + ENC_SUFFIX;
        } catch (Exception e) {
            throw new RuntimeException("凭据加密失败", e);
        }
    }

    /**
     * 解密密文。若入参非 {@code ENC(...)} 形式则原样返回（兼容历史明文）。
     *
     * @param cipherText 密文或明文
     * @return 明文
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty() || !isEncrypted(cipherText)) {
            return cipherText;
        }
        String inner = cipherText.substring(ENC_PREFIX.length(),
                cipherText.length() - ENC_SUFFIX.length());
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(inner);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("凭据解密失败", e);
        }
    }

    /** 判断字符串是否为加密标记形式。 */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX)
                && value.length() > ENC_PREFIX.length() + ENC_SUFFIX.length();
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

    private static byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 摘要失败", e);
        }
    }
}
