package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy;
import cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo.CutoverCreationOptionRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pms/cutover-tasks")
@RequiredArgsConstructor
public class CutoverIntakeOptionsController {
    private final CutoverIntakeConfigurationPolicy configuration;

    @GetMapping("/creation-options")
    @PreAuthorize("@ss.hasPermission('pms:cutover-task:create')")
    public CommonResult<List<CutoverCreationOptionRespVO>> options() {
        return CommonResult.success(configuration.available(TenantContextHolder.getRequiredTenantId()).stream()
                .map(CutoverCreationOptionRespVO::from).toList());
    }
}
