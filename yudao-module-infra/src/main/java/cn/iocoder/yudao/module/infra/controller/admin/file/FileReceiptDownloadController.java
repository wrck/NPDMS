package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.infra.service.file.FileReceiptDownloadService;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class FileReceiptDownloadController {
    private final FileReceiptDownloadService downloads;

    /** Like private object-storage URLs, this endpoint requires an unexpired opaque capability. */
    @GetMapping(FileReceiptDownloadService.PATH)
    @PermitAll
    @TenantIgnore
    @ApiAccessLog(enable = false, requestEnable = false, responseEnable = false)
    public void download(@RequestParam(required = false) String ticket, HttpServletResponse response) throws Exception {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        var file = downloads.read(ticket);
        if (file == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", ContentDisposition.attachment()
                .filename(file.name(), StandardCharsets.UTF_8).build().toString());
        response.setContentLengthLong(file.content().length);
        response.getOutputStream().write(file.content());
    }
}
