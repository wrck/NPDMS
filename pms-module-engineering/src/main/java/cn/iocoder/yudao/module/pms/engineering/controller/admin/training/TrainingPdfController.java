package cn.iocoder.yudao.module.pms.engineering.controller.admin.training;

import cn.iocoder.yudao.module.pms.engineering.service.training.TrainingService;
import jakarta.annotation.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/pms/training-records")
public class TrainingPdfController {
    @Resource private TrainingService trainingService;

    @GetMapping("/{id}/pdf")
    @PreAuthorize("@ss.hasPermission('pms:imp-training:query')")
    public ResponseEntity<Void> download(@PathVariable("id") Long id) throws IOException {
        String url=trainingService.requestPdfDownload(id);
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(url)).cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/{id}/pdf:access-ticket")
    @PreAuthorize("@ss.hasPermission('pms:imp-training:query')")
    public cn.iocoder.yudao.framework.common.pojo.CommonResult<String> pdfTicket(@PathVariable("id") Long id) throws IOException {
        return cn.iocoder.yudao.framework.common.pojo.CommonResult.success(trainingService.requestPdfDownload(id));
    }
    @GetMapping("/{id}/files/{materialId}")
    @PreAuthorize("@ss.hasPermission('pms:imp-training:query')")
    public cn.iocoder.yudao.framework.common.pojo.CommonResult<String> generatedFileTicket(@PathVariable("id") Long id,@PathVariable("materialId") Long materialId) {
        return cn.iocoder.yudao.framework.common.pojo.CommonResult.success(trainingService.requestGeneratedFileDownload(id,materialId));
    }
}
