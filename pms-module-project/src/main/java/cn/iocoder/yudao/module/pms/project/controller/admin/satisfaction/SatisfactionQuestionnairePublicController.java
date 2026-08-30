package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.SatisfactionAccessGrantService;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/satisfaction-questionnaires")
@RequiredArgsConstructor
public class SatisfactionQuestionnairePublicController {
    private final SatisfactionAccessGrantService grantService;

    @GetMapping("/{token}")
    @PermitAll
    public CommonResult<SatisfactionAccessGrantService.PublicQuestionnaire> inspect(
            @PathVariable("token") String token) {
        return success(grantService.inspect(TenantContextHolder.getRequiredTenantId(), token));
    }
}
