package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ArrivalEvidenceRetryJob implements JobHandler {

    static final int BATCH_SIZE = 50;

    private final ArrivalEvidenceRetryService retryService;
    private final Environment environment;

    @Override
    @TenantJob
    public String execute(String param) {
        if (TenantContextHolder.getTenantId() != null) {
            return retryDueEvidence();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            TenantContextHolder.getRequiredTenantId();
        }
        String[] result = new String[1];
        TenantUtils.execute(0L, () -> result[0] = retryDueEvidence());
        return result[0];
    }

    private String retryDueEvidence() {
        LocalDateTime dueAt = LocalDateTime.now();
        int processed = 0;
        while (processed < BATCH_SIZE && retryService.retryNext(dueAt)) {
            processed++;
        }
        return "到货签收证据业务重试处理 " + processed + " 条";
    }
}
