package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationBatchCreateReqVO;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/implementation/installation-records")
public class ProjectInstallationBatchController {
    private final InstallationService service;
    private final ProjectScopeApi scope;

    @PostMapping("/batch")
    @PreAuthorize("@ss.hasPermission('pms:imp-installation:create')")
    public CommonResult<List<Long>> create(@Valid @RequestBody InstallationBatchCreateReqVO request) {
        var visible = scope.resolveCurrent(new ProjectCurrentScopeQuery(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), request.getProjectId(), ProjectScopeApi.ACTION_EDIT));
        if (visible == null || visible.fullProjectIds() == null || !visible.fullProjectIds().contains(request.getProjectId())) {
            throw exception(FORBIDDEN);
        }
        return CommonResult.success(service.createInstallationBatch(request));
    }
}
