package cn.iocoder.yudao.module.pms.project.controller.admin.legacydetail;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailInput;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyDetailService;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLegacyDetailService.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/projects/{projectId}/legacy-detail")
@RequiredArgsConstructor
public class ProjectLegacyDetailController {
    private final ProjectLegacyDetailService service;
    @PostMapping("/imports")
    @PreAuthorize("@ss.hasPermission('pms:integration:execute') && @ss.hasPermission('pms:project:update')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<ImportResult> ingest(@PathVariable Long projectId,@Valid @RequestBody LegacyDetailInput input) {
        return success(service.ingest(projectId,input,actor()));
    }
    @GetMapping("/sources")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<List<SourceView>> sources(@PathVariable Long projectId) {
        return success(service.sources(projectId,actor()));
    }
    @GetMapping("/records")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<PageResult<RecordView>> records(@PathVariable Long projectId,@RequestParam Long sourceId,
            @RequestParam(required=false) Long snapshotId,@RequestParam String domain,
            @RequestParam(required=false) String sourceKey,@RequestParam(required=false) String parentDomain,
            @RequestParam(required=false) String parentKey,@RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="50") int pageSize) {
        return success(service.records(projectId,sourceId,snapshotId,domain,sourceKey,parentDomain,parentKey,pageNo,pageSize,actor()));
    }
    private static ProjectAccessActor actor() {
        return new ProjectAccessActor(TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId());
    }
}
