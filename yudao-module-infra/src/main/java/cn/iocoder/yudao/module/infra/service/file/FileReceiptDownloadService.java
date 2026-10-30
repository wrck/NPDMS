package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Storage capability issued only after the owning business service has authorized file access. */
@Service
@RequiredArgsConstructor
public class FileReceiptDownloadService {
    public static final String PATH = "/infra/file-storage-receipts/content";
    private static final String KEY_PREFIX = "infra:file-receipt-access:";
    private final StringRedisTemplate redis;
    private final FileMapper files;
    private final FileConfigService configs;
    private final WebProperties web;
    private final SecureRandom random = new SecureRandom();

    public String issue(FileDO file, int seconds) {
        if (seconds <= 0 || file.getId() == null || !isReceiptPath(file.getPath())) {
            throw new IllegalArgumentException("FILE_RECEIPT_ACCESS_INVALID");
        }
        FilePathUtils.validatePath(file.getPath());
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(web.getAdminApi().getPrefix()).path(PATH).queryParam("ticket", token).build().toUriString();
        Binding binding = new Binding(file.getId(), file.getConfigId(), file.getPath(),
                System.currentTimeMillis() + Duration.ofSeconds(seconds).toMillis());
        redis.opsForValue().set(key(token), JsonUtils.toJsonString(binding), Duration.ofSeconds(seconds));
        return url;
    }

    public Download read(String token) throws Exception {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        String raw = redis.opsForValue().get(key(token));
        if (raw == null) return null;
        Binding binding = JsonUtils.parseObjectQuietly(raw, Binding.class);
        if (binding == null || binding.expiresAt() <= System.currentTimeMillis()) return null;
        FileDO file = files.selectById(binding.fileId());
        if (file == null || !Objects.equals(file.getConfigId(), binding.configId())
                || !Objects.equals(file.getPath(), binding.path()) || !isReceiptPath(file.getPath())) return null;
        FilePathUtils.validatePath(file.getPath());
        var client = configs.getFileClient(file.getConfigId());
        if (client == null) return null;
        byte[] content = client.getContent(file.getPath());
        return content == null ? null : new Download(FilePathUtils.validateFileName(file.getName()), content);
    }

    public static boolean isReceiptPath(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).startsWith("pms-storage-receipts/");
    }

    static String key(String token) {
        try {
            return KEY_PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Binding(Long fileId, Long configId, String path, long expiresAt) { }
    public record Download(String name, byte[] content) { }
}
