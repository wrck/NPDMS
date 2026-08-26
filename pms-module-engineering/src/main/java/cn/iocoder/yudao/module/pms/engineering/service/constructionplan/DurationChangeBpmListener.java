package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.DURATION_CHANGE_BPM_ASSOCIATION_INVALID;

/** 同步消费项目工期审批流程终态。 */
@Component
@RequiredArgsConstructor
public class DurationChangeBpmListener extends BpmProcessInstanceStatusEventListener {

    private final DurationChangeProperties properties;
    private final DurationChangeBpmResultService resultService;
    private final Environment environment;

    @Override
    protected String getProcessDefinitionKey() {
        return properties.getProcessDefinitionKey();
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        if (TenantContextHolder.getTenantId() != null) {
            resultService.handle(event.getId(), event.getStatus(), event.getReason());
            return;
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            throw exception(DURATION_CHANGE_BPM_ASSOCIATION_INVALID);
        }
        TenantUtils.execute(0L,
                () -> resultService.handle(event.getId(), event.getStatus(), event.getReason()));
    }

}
