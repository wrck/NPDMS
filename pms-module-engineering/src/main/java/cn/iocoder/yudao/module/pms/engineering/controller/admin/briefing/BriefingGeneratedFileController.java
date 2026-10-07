package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.engineering.service.briefing.BriefingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/briefings")
public class BriefingGeneratedFileController {
    private final BriefingService service;
    @GetMapping("/{id}/files/{materialId}")
    @PreAuthorize("@ss.hasPermission('pms:sol-briefing:query')")
    public CommonResult<String> download(@PathVariable("id") Long id,@PathVariable("materialId") Long materialId) {
        return CommonResult.success(service.requestGeneratedFileDownload(id,materialId));
    }
}
