package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.file.FileSecurityScanProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileSecurityScanResult;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileContentValidationCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_DIGEST_MISMATCH;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_MEDIA_TYPE_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SECURITY_SCAN_REJECTED;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SECURITY_SCAN_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SIZE_EXCEEDED;

@Service
public class FileContentPolicyService {

    private static final Tika TIKA = new Tika();

    private final BoundedMultipartReader multipartReader;
    private final List<FileSecurityScanProvider> scanProviders;

    public FileContentPolicyService(BoundedMultipartReader multipartReader,
                                    List<FileSecurityScanProvider> scanProviders) {
        this.multipartReader = multipartReader;
        this.scanProviders = List.copyOf(scanProviders);
    }

    public ValidatedFileContent validate(FileContentValidationCommand command) {
        validateCommand(command);
        FileBusinessObjectPolicyFact policy = command.policy();
        long maxBytes = Math.min(FileUploadApplicationService.PLATFORM_MAX_BYTES, policy.maxSizeBytes());
        if (maxBytes <= 0) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        byte[] content = multipartReader.read(command.file(), maxBytes);
        return validateBounded(new BoundedFileContentValidationCommand(
                content, command.expectedFileName(), command.declaredSizeBytes(),
                command.declaredMediaType(), command.clientSha256(), command.policy()));
    }

    public ValidatedFileContent validateBounded(BoundedFileContentValidationCommand command) {
        if (command == null || command.content() == null || command.content().length == 0
                || command.expectedFileName() == null || command.expectedFileName().isBlank()
                || command.declaredSizeBytes() <= 0 || command.declaredMediaType() == null
                || command.declaredMediaType().isBlank() || command.policy() == null
                || command.policy().maxSizeBytes() == null) {
            throw exception(FILE_COMMAND_INVALID);
        }
        FileBusinessObjectPolicyFact policy = command.policy();
        long maxBytes = Math.min(FileUploadApplicationService.PLATFORM_MAX_BYTES, policy.maxSizeBytes());
        byte[] content = command.content();
        if (content.length > maxBytes) {
            throw exception(FILE_SIZE_EXCEEDED);
        }
        if (content.length != command.declaredSizeBytes()) {
            throw exception(FILE_COMMAND_INVALID);
        }

        String declaredMediaType = normalizeMediaType(command.declaredMediaType());
        String detectedMediaType = normalizeMediaType(TIKA.detect(content));
        String nameMediaType = normalizeMediaType(TIKA.detect(command.expectedFileName()));
        Set<String> allowedMediaTypes = command.policy().allowedMediaTypes().stream()
                .map(FileContentPolicyService::normalizeMediaType)
                .collect(Collectors.toUnmodifiableSet());
        if (!allowedMediaTypes.contains(declaredMediaType)
                || !declaredMediaType.equals(detectedMediaType)
                || !declaredMediaType.equals(nameMediaType)) {
            throw exception(FILE_MEDIA_TYPE_INVALID);
        }

        String sha256 = sha256(content);
        String clientSha256 = normalizeDigest(command.clientSha256());
        if (clientSha256 != null && !MessageDigest.isEqual(
                sha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                clientSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw exception(FILE_DIGEST_MISMATCH);
        }

        FileSecurityScanResult scan = scan(content, command.expectedFileName(), declaredMediaType,
                detectedMediaType, sha256);
        return new ValidatedFileContent(content, content.length, sha256, detectedMediaType,
                extension(detectedMediaType), scan.providerCode(), scan.providerVersion());
    }

    private FileSecurityScanResult scan(byte[] content, String fileName, String declaredMediaType,
                                        String detectedMediaType, String sha256) {
        if (scanProviders.size() != 1) {
            throw exception(FILE_SECURITY_SCAN_UNAVAILABLE);
        }
        FileSecurityScanResult result;
        try {
            result = scanProviders.getFirst().scan(new FileSecurityScanCommand(
                    content, fileName, declaredMediaType, detectedMediaType, sha256));
        } catch (RuntimeException ex) {
            throw exception(FILE_SECURITY_SCAN_UNAVAILABLE);
        }
        if (result == null || "ERROR".equals(result.resultCode())) {
            throw exception(FILE_SECURITY_SCAN_UNAVAILABLE);
        }
        if ("REJECTED".equals(result.resultCode())) {
            throw exception(FILE_SECURITY_SCAN_REJECTED);
        }
        if (!"PASSED".equals(result.resultCode())
                || result.providerCode() == null || result.providerCode().isBlank()) {
            throw exception(FILE_SECURITY_SCAN_UNAVAILABLE);
        }
        return result;
    }

    private static void validateCommand(FileContentValidationCommand command) {
        if (command == null || command.file() == null || command.file().isEmpty()
                || command.expectedFileName() == null || command.expectedFileName().isBlank()
                || command.declaredSizeBytes() <= 0 || command.declaredMediaType() == null
                || command.declaredMediaType().isBlank() || command.policy() == null
                || command.policy().maxSizeBytes() == null) {
            throw exception(FILE_COMMAND_INVALID);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String normalizeDigest(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return normalized;
    }

    private static String normalizeMediaType(String value) {
        if (value == null || value.isBlank()) {
            throw exception(FILE_MEDIA_TYPE_INVALID);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int parameter = normalized.indexOf(';');
        return parameter < 0 ? normalized : normalized.substring(0, parameter).trim();
    }

    private static String extension(String mediaType) {
        try {
            String extension = MimeTypes.getDefaultMimeTypes().forName(mediaType).getExtension();
            if (extension == null || extension.isBlank()) {
                throw exception(FILE_MEDIA_TYPE_INVALID);
            }
            return extension.startsWith(".") ? extension.substring(1) : extension;
        } catch (MimeTypeException ex) {
            throw exception(FILE_MEDIA_TYPE_INVALID);
        }
    }
}
