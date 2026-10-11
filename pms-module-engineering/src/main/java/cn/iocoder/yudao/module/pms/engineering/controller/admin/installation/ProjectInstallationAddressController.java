package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationAddressCompletionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/implementation/projects/{projectId}/installation-address-completion")
public class ProjectInstallationAddressController {
    private final InstallationAddressCompletionApi addresses;
    private final ProjectScopeApi scope;
    @GetMapping @Transactional
    @PreAuthorize("@ss.hasPermission('pms:imp-installation:query')")
    public CommonResult<Map<String, Object>> inspect(@PathVariable Long projectId) {
        var tenant = TenantContextHolder.getTenantId();
        var visible = scope.resolveCurrent(new ProjectCurrentScopeQuery(tenant, SecurityFrameworkUtils.getLoginUserId(), projectId, ProjectScopeApi.ACTION_VIEW));
        if (visible == null || visible.fullProjectIds() == null || !visible.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        var fact = addresses.lockAndInspect(new InstallationAddressCompletionApi.Query(tenant, projectId));
        return CommonResult.success(Map.of("totalDevices", fact.totalDevices(), "addressedDevices", fact.addressedDevices(), "completed", fact.completed()));
    }
}
