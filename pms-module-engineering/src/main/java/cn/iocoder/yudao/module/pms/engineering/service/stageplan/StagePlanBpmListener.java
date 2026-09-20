package cn.iocoder.yudao.module.pms.engineering.service.stageplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** 同步消费阶段施工计划审批流程终态。 */
@Component
@RequiredArgsConstructor
public class StagePlanBpmListener extends BpmProcessInstanceStatusEventListener {

    private final StagePlanProperties properties;
    private final StagePlanBatchService stagePlanBatchService;
    private final Environment environment;

    @Override
    protected String getProcessDefinitionKey() {
        return properties.getProcessDefinitionKey();
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        if (TenantContextHolder.getTenantId() != null) {
            stagePlanBatchService.handleBpmResult(event.getId(), event.getStatus(), event.getReason());
            return;
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(ErrorCodeConstants.STAGE_PLAN_BPM_ASSOCIATION_INVALID);
        }
        TenantUtils.execute(0L, () -> stagePlanBatchService.handleBpmResult(
                event.getId(), event.getStatus(), event.getReason()));
    }
}
