package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.module.bpm.api.normalclosure.BpmNormalClosureResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Synchronous consumption inside the Owner's before-commit bridge; exceptions roll back real BPM approval. */
@Component @RequiredArgsConstructor
public class NormalClosureBpmListener {
    private final NormalClosureApplicationService application;
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void onResult(BpmNormalClosureResultEvent event) {
        application.onBpmResult(event.tenantId(), event.processInstanceId());
    }
}
