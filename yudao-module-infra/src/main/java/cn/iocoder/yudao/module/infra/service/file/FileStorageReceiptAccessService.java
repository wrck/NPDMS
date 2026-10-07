package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** DB 文件客户端不支持存储侧签名时的短时 bearer 读取；只冻结已登记的 receipt 文件。 */
@Service
@RequiredArgsConstructor
public class FileStorageReceiptAccessService {

    private static final String KEY_PREFIX = "infra:file-storage-receipt-access:";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final FileMapper fileMapper;
    private final FileConfigService fileConfigService;
    private final WebProperties webProperties;

    public String issue(FileDO file, int expirationSeconds) {
        if (file == null || file.getId() == null || file.getConfigId() == null || !FileReceiptDownloadService.isReceiptPath(file.getPath())
                || file.getName() == null || file.getType() == null || file.getSize() == null || expirationSeconds <= 0) {
            throw new IllegalArgumentException("FILE_STORAGE_RECEIPT_DB_ACCESS_INVALID");
        }
        FilePathUtils.validatePath(file.getPath());
        String urlBase = receiptConfigDomain(file.getConfigId());
        String token = newToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expirationSeconds);
        var ticket = new ReceiptTicket(file.getId(), file.getConfigId(), file.getPath(), file.getName(),
                file.getType(), file.getSize(), expiresAt);
        redisTemplate.opsForValue().set(key(token), JsonUtils.toJsonString(ticket), Duration.ofSeconds(expirationSeconds));
        return urlBase + endpointPath() + "?ticket=" + token;
    }

    /** Returns null for an unknown, expired or changed ticket without exposing the reason to the caller. */
    public ReceiptContent read(String token) throws Exception {
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()) return null;
        String serialized = redisTemplate.opsForValue().get(key(token));
        if (serialized == null) return null;
        ReceiptTicket ticket;
        try {
            ticket = JsonUtils.parseObject(serialized, ReceiptTicket.class);
        } catch (RuntimeException malformed) {
            return null;
        }
        if (ticket == null || ticket.expiresAt() == null || !ticket.expiresAt().isAfter(LocalDateTime.now())) return null;
        FileDO current = fileMapper.selectById(ticket.infraFileId());
        if (!matches(current, ticket) || !FileReceiptDownloadService.isReceiptPath(ticket.path())) return null;
        byte[] content = fileConfigService.getFileClient(ticket.configId()).getContent(ticket.path());
        if (content == null || content.length != ticket.sizeBytes()) return null;
        return new ReceiptContent(ticket.name(), ticket.mediaType(), content);
    }

    private String receiptConfigDomain(Long configId) {
        FileConfigDO config = fileConfigService.getFileConfig(configId);
        if(config==null)throw new IllegalStateException("FILE_STORAGE_RECEIPT_CONFIG_INVALID");
        if(config.getConfig() instanceof DBFileClientConfig database)return receiptDomain(database.getDomain(),configId);
        if(config.getConfig() instanceof cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClientConfig local)
            return receiptDomain(local.getDomain(),configId);
        throw new IllegalStateException("FILE_STORAGE_RECEIPT_CONFIG_INVALID");
    }

    private String receiptDomain(String configuredDomain,Long configId) {
        String normalized=normalizeDomain(configuredDomain);
        String prefix=webProperties.getAdminApi().getPrefix();
        if(prefix==null || prefix.isBlank())throw new IllegalStateException("FILE_STORAGE_RECEIPT_ADMIN_API_INVALID");
        prefix=StrUtil.removeSuffix(prefix.startsWith("/")?prefix:"/"+prefix,"/");
        String legacySuffix=prefix+"/infra/file/"+configId+"/get";
        // Existing DB/local configuration domains may already name the legacy download route.
        return normalized.endsWith(legacySuffix)?normalized.substring(0,normalized.length()-legacySuffix.length()):normalized;
    }

    private String normalizeDomain(String domain) {
        try {
            URI uri = URI.create(domain == null ? "" : domain.trim());
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("invalid receipt access domain");
            }
            return StrUtil.removeSuffix(uri.toString(), "/");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("FILE_STORAGE_RECEIPT_DB_CONFIG_INVALID");
        }
    }

    private String endpointPath() {
        String prefix = webProperties.getAdminApi().getPrefix();
        if (prefix == null || prefix.isBlank()) throw new IllegalStateException("FILE_STORAGE_RECEIPT_ADMIN_API_INVALID");
        return StrUtil.removeSuffix(prefix.startsWith("/") ? prefix : "/" + prefix, "/")
                + "/infra/file-storage-receipts/content";
    }

    private boolean matches(FileDO file, ReceiptTicket ticket) {
        return file != null && Objects.equals(file.getId(), ticket.infraFileId())
                && Objects.equals(file.getConfigId(), ticket.configId()) && Objects.equals(file.getPath(), ticket.path())
                && Objects.equals(file.getName(), ticket.name()) && Objects.equals(file.getType(), ticket.mediaType())
                && Objects.equals(file.getSize(), ticket.sizeBytes());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256摘要算法不可用", exception);
        }
    }

    public record ReceiptTicket(Long infraFileId, Long configId, String path, String name,
                                String mediaType, Long sizeBytes, LocalDateTime expiresAt) {
    }

    public record ReceiptContent(String name, String mediaType, byte[] content) {
    }
}
