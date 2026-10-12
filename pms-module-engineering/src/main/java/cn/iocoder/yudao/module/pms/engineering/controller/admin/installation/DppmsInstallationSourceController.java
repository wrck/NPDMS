package cn.iocoder.yudao.module.pms.engineering.controller.admin.installation;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@RequestMapping("/api/v1/pms/installation-source-records")
@RequiredArgsConstructor
public class DppmsInstallationSourceController {
    private final DppmsInstallationSourceQueryApi histories;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:device:query') or @ss.hasPermission('pms:imp-installation:query')")
    public CommonResult<PageResult<DppmsInstallationSourceQueryApi.SourceRecord>> page(
            @RequestParam(required=false) Long projectId,@RequestParam(required=false) Long equipmentId,
            @RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize) {
        return success(histories.pageSourceRecords(new DppmsInstallationSourceQueryApi.Query(
                TenantContextHolder.getRequiredTenantId(),SecurityFrameworkUtils.getLoginUserId(),
                projectId,equipmentId,pageNo,pageSize)));
    }
}
