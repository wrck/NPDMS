package cn.iocoder.yudao.module.pms.service.controller.business;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionLogDownloadService;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionRequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/inspection-tasks/{taskId}/executions/{executionId}")
public class InspectionLogDownloadController {
    private final InspectionLogDownloadService service;

    @GetMapping("/redacted-log")
    @PreAuthorize("@ss.hasPermission('pms:inspection-task:query') and @ss.hasPermission('pms:file:download')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public ResponseEntity<byte[]> download(@PathVariable Long taskId, @PathVariable Long executionId) {
        var file = service.download(new InspectionLogDownloadService.Query(InspectionRequestContext.current(), taskId, executionId));
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(file.content());
    }
}
