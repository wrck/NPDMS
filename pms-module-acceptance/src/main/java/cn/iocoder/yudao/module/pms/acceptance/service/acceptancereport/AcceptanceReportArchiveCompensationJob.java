package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/** 报告投影材料归档补偿调度：平台材料台账按租户扫描 PENDING，投影路由后逐条推进。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AcceptanceReportArchiveCompensationJob implements JobHandler {

    private static final int BATCH_SIZE = 20;
    private final PlatformDeliveryRequirementApi platform;
    private final AcceptanceReportArchiveCompensationService compensationService;
    private final Environment environment;

    @Override
    @TenantJob
    public String execute(String param) {
        if (TenantContextHolder.getTenantId() != null) {
            return archivePendingMaterials();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            TenantContextHolder.getRequiredTenantId();
        }
        String[] result = new String[1];
        TenantUtils.execute(0L, () -> result[0] = archivePendingMaterials());
        return result[0];
    }

    private String archivePendingMaterials() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<TemplateFrozenSubmissionView> submissions = platform.listPendingArchiveSubmissions();
        int archived = 0;
        int pending = 0;
        int scanned = 0;
        for (TemplateFrozenSubmissionView submission : submissions) {
            if (scanned >= BATCH_SIZE) break;
            if (!reportProjection(submission)) continue;
            scanned++;
            try {
                compensationService.archiveSubmission(tenantId, submission.id());
                archived++;
            } catch (RuntimeException failure) {
                compensationService.recordSubmissionFailure(tenantId, submission.id(), "ARCHIVE_FAILED");
                pending++;
                log.warn("[execute][报告投影材料({})归档失败，保留待补偿]", submission.id(), failure);
            }
        }
        return String.format("报告归档成功 %d 条，继续待补偿 %d 条", archived, pending);
    }

    private static boolean reportProjection(TemplateFrozenSubmissionView submission) {
        return PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(submission.sourceType())
                && submission.requestKey() != null && submission.requestKey().startsWith("report:");
    }
}
