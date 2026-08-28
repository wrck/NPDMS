package cn.iocoder.yudao.module.pms.engineering.api.readiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessFact;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_PROJECT_FACT_INVALID;

@Service
@RequiredArgsConstructor
public class SiteSurveyReadinessApiImpl implements SiteSurveyReadinessApi {

    private final PreparationReadinessService readinessService;

    @Override
    public SiteSurveyReadinessFact inspect(SiteSurveyReadinessQuery query) {
        return readinessService.inspect(query, trustedTenantId(), trustedActorId());
    }

    @Override
    public SiteSurveyReadinessFact lockAndRevalidate(SiteSurveyReadinessRevalidationQuery query) {
        return readinessService.lockAndRevalidate(query, trustedTenantId(), trustedActorId());
    }

    private Long trustedTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        return tenantId;
    }

    private Long trustedActorId() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) throw exception(PREPARATION_PROJECT_FACT_INVALID);
        return actorId;
    }
}
