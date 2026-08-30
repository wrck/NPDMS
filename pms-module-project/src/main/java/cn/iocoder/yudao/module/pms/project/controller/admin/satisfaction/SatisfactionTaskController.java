package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo.SatisfactionAccessGrantCreateReqVO;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAccessGrantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-tasks")
@Validated
@RequiredArgsConstructor
public class SatisfactionTaskController {
    private final SatisfactionAccessGrantService grantService;

    @PostMapping("/{id}/access-grants")
    @PreAuthorize("@ss.hasPermission('pms:acceptance:satisfaction:manage')")
    public CommonResult<SatisfactionAccessGrantService.CreatedGrant> createGrant(
            @PathVariable("id") Long taskId,
            @Valid @RequestBody SatisfactionAccessGrantCreateReqVO request) {
        return success(grantService.create(TenantContextHolder.getRequiredTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), taskId, request.getExpiresAt()));
    }
}
