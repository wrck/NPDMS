package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityCompletionCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityCompletionFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceReportIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityCompleteUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AcceptanceActivityCompletionFactApiImpl implements AcceptanceActivityCompletionFactApi {

    private final AcceptanceActivityMapper activityMapper;
    private final AcceptanceReportVersionMapper reportMapper;
    private final AcceptanceReportAttachmentMapper attachmentMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public AcceptanceActivityCompletionFact lockAndComplete(AcceptanceActivityCompletionCommand command) {
        if (!valid(command)) return fact("IDENTITY_MISMATCH", null, null, null);
        AcceptanceActivityDO activity = activityMapper.selectByIdForUpdate(
                new AcceptanceActivityIdLockQuery(command.tenantId(), command.acceptanceId()));
        if (activity == null || !Objects.equals(activity.getProjectId(), command.projectId())
                || !Objects.equals(activity.getProjectTaskId(), command.projectTaskId())
                || !Objects.equals(activity.getExecutionContractId(), command.executionContractId())) {
            return fact("IDENTITY_MISMATCH", null, null, null);
        }
        if (!Objects.equals(activity.getVersion(), command.expectedActivityVersion())) {
            return fact("VERSION_CONFLICT", activity, null, null);
        }
        if ("COMPLETED".equals(activity.getActivityStatus())) {
            AcceptanceReportVersionDO completedReport = currentReport(activity);
            return completedReport != null
                    && Objects.equals(completedReport.getReportVersionNo(), command.expectedReportVersion())
                    ? fact("COMPLETED", activity, completedReport.getId(), completedReport.getReportVersionNo())
                    : fact("VERSION_CONFLICT", activity, null, null);
        }
        if (!"PENDING".equals(activity.getActivityStatus()) || activity.getCurrentReportVersionId() == null) {
            return fact("REPORT_INCOMPLETE", activity, null, null);
        }
        AcceptanceReportVersionDO report = currentReport(activity);
        if (report == null || !Objects.equals(report.getReportVersionNo(), command.expectedReportVersion())) {
            return fact("VERSION_CONFLICT", activity, null, null);
        }
        if (!complete(report) || attachmentMapper.selectByReportVersion(report.getId()).isEmpty()) {
            return fact("REPORT_INCOMPLETE", activity, report.getId(), report.getReportVersionNo());
        }
        int updated = activityMapper.completeIfPending(new AcceptanceActivityCompleteUpdate(
                command.tenantId(), activity.getId(), activity.getVersion(), "acceptance-completion-api"));
        if (updated != 1) return fact("VERSION_CONFLICT", activity, null, null);
        activity.setActivityStatus("COMPLETED");
        activity.setVersion(activity.getVersion() + 1);
        return fact("COMPLETED", activity, report.getId(), report.getReportVersionNo());
    }

    private AcceptanceReportVersionDO currentReport(AcceptanceActivityDO activity) {
        if (activity.getCurrentReportVersionId() == null) return null;
        AcceptanceReportVersionDO report = reportMapper.selectByIdForUpdate(
                new AcceptanceReportIdLockQuery(activity.getTenantId(), activity.getId(),
                        activity.getCurrentReportVersionId()));
        return report != null && "EFFECTIVE".equals(report.getReportStatus()) && report.getEffectiveTo() == null
                ? report : null;
    }

    private boolean complete(AcceptanceReportVersionDO report) {
        return report.getAcceptanceTime() != null && notBlank(report.getConclusionCode())
                && notBlank(report.getAcceptorName());
    }

    private boolean valid(AcceptanceActivityCompletionCommand command) {
        Long tenantId = TenantContextHolder.getTenantId();
        return command != null && tenantId != null && tenantId.equals(command.tenantId())
                && command.projectId() != null && command.projectId() > 0
                && command.projectTaskId() != null && command.projectTaskId() > 0
                && command.executionContractId() != null && command.executionContractId() > 0
                && command.acceptanceId() != null && command.acceptanceId() > 0
                && command.expectedActivityVersion() != null && command.expectedActivityVersion() >= 0
                && command.expectedReportVersion() != null && command.expectedReportVersion() > 0
                && notBlank(command.operationId());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private AcceptanceActivityCompletionFact fact(String outcome, AcceptanceActivityDO activity,
                                                   Long reportId, Integer reportVersion) {
        return new AcceptanceActivityCompletionFact(outcome, activity == null ? null : activity.getId(),
                activity == null ? null : activity.getVersion(), reportId, reportVersion);
    }
}
