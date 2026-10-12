package cn.iocoder.yudao.module.pms.project.controller.admin.legacydetail;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectAssociatedDataService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@RequestMapping("/api/v1/pms/projects/{projectId}/associated-data")
@RequiredArgsConstructor
public class ProjectAssociatedDataController {
    private final ProjectAssociatedDataService service;
    private final cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectLicenseRecordMigrationService licenses;
    @GetMapping("/records")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<PageResult<ProjectAssociatedDataService.Row>> page(@PathVariable Long projectId,
            @RequestParam String kind,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="50") int pageSize) {
        var actor=new ProjectAccessActor(TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId());
        return success("licenses".equals(kind)?licenses.page(projectId,pageNo,pageSize,actor):service.page(projectId,kind,pageNo,pageSize,actor));
    }
}
