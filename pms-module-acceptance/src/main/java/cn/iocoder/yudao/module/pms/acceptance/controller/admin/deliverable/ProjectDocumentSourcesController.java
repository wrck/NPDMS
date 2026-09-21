package cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverable;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDocumentSourceRegistry;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/project-document-sources")
public class ProjectDocumentSourcesController {
    private final ProjectDocumentSourceRegistry sources;
    @GetMapping @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<List<FileDocumentSourceProvider.Descriptor>> list() { return CommonResult.success(sources.descriptors()); }
}
