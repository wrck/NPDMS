package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.infra.service.file.FileStorageReceiptAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** 已经由 PLT 授权的 DB receipt 短时 bearer 内容读取；不接受存储标识或路径。 */
@Tag(name = "管理后台 - 文件存储回执受控读取")
@RestController
@RequestMapping("/infra/file-storage-receipts")
@RequiredArgsConstructor
@Slf4j
public class FileStorageReceiptContentController {

    private static final Set<String> INLINE_MEDIA_TYPES = Set.of(
            "application/pdf", "image/gif", "image/jpeg", "image/png", "image/webp", "text/plain");

    private final FileStorageReceiptAccessService accessService;

    @GetMapping("/content")
    @PermitAll
    @TenantIgnore
    @ApiAccessLog(enable = false)
    @Operation(summary = "读取受控文件存储回执")
    public void content(@RequestParam(value = "ticket", required = false) String ticket,
                        HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        try {
            var receipt = accessService.read(ticket);
            if (receipt == null) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                return;
            }
            write(response, receipt);
        } catch (Exception failure) {
            // The ticket is a bearer secret and must never be propagated to API/error logs.
            log.warn("[content][receipt storage read failed: {}]", failure.getClass().getSimpleName());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    private void write(HttpServletResponse response, FileStorageReceiptAccessService.ReceiptContent receipt) throws Exception {
        String mediaType = normalizeMediaType(receipt.mediaType());
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(mediaType);
        response.setContentLengthLong(receipt.content().length);
        String disposition = INLINE_MEDIA_TYPES.contains(MediaType.parseMediaType(mediaType).getType()
                + "/" + MediaType.parseMediaType(mediaType).getSubtype()) ? "inline" : "attachment";
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(disposition)
                .filename(receipt.name(), StandardCharsets.UTF_8).build().toString());
        response.getOutputStream().write(receipt.content());
    }

    private String normalizeMediaType(String mediaType) {
        try {
            return MediaType.parseMediaType(mediaType).toString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException invalid) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
