package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class AcceptanceDocumentSources implements FileDocumentSourceProvider {
    private final AcceptanceActivityMapper activities;
    private final AcceptanceReportVersionMapper reports;
    private final SatisfactionResultMapper results;
    private final SatisfactionResponseMapper responses;
    private final SatisfactionQuestionnaireMapper questionnaires;
    private final SatisfactionCollectionTaskMapper tasks;
    @Override public List<Descriptor> descriptors() { return List.of(
            new Descriptor("ACC.PRELIMINARY_REPORT", "初验报告附件"),
            new Descriptor("ACC.FINAL_REPORT", "终验报告附件"),
            new Descriptor("ACC.SATISFACTION_RESPONSE", "Satisfaction response evidence"),
            new Descriptor("ACC.SATISFACTION_DOCUMENT", "满意度结果文档")); }
    @Override public Scope resolve(Long tenant, String owner, String type, String object, String purpose) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || !"ACC".equals(owner)) return null;
        Long id;
        try { id = Long.valueOf(object); } catch (RuntimeException invalid) { return null; }
        if ("ACCEPTANCE_REPORT_VERSION".equals(type) && "ACCEPTANCE_REPORT_ATTACHMENT".equals(purpose)) {
            var report = reports.selectById(id);
            if (report == null || Boolean.TRUE.equals(report.getDeleted()) || !tenant.equals(report.getTenantId())) return null;
            var activity = activities.selectById(report.getAcceptanceId());
            if (activity == null || Boolean.TRUE.equals(activity.getDeleted()) || !tenant.equals(activity.getTenantId())) return null;
            if (!Set.of("PRELIMINARY", "FINAL").contains(activity.getAcceptanceType())) return null;
            return new Scope(activity.getProjectId(), "ACC." + activity.getAcceptanceType() + "_REPORT", "ACC", "acceptanceActivity", activity.getId(), report.getId());
        }
        if ("SATISFACTION_RESPONSE".equals(type) && Set.of("SATISFACTION_SIGNATURE","SATISFACTION_ATTACHMENT").contains(purpose)) {
            var response=responses.selectById(id);
            if(response==null || !tenant.equals(response.getTenantId()))return null;
            var questionnaire=questionnaires.selectById(response.getQuestionnaireId());
            if(questionnaire==null || Boolean.TRUE.equals(questionnaire.getDeleted()) || !tenant.equals(questionnaire.getTenantId()))return null;
            var task=tasks.selectById(questionnaire.getCollectionTaskId());
            if(task==null || Boolean.TRUE.equals(task.getDeleted()) || !tenant.equals(task.getTenantId()))return null;
            return new Scope(task.getProjectId(),"ACC.SATISFACTION_RESPONSE","ACC","satisfactionCollectionTask",task.getId(),response.getId());
        }
        if ("SATISFACTION_RESULT".equals(type) && "SATISFACTION_RESULT_DOCUMENT".equals(purpose)) {
            var result = results.selectById(id);
            if (result == null || !tenant.equals(result.getTenantId())) return null;
            var task = tasks.selectById(result.getCollectionTaskId());
            if (task == null || Boolean.TRUE.equals(task.getDeleted()) || !tenant.equals(task.getTenantId())) return null;
            return new Scope(task.getProjectId(), "ACC.SATISFACTION_DOCUMENT", "ACC", "satisfactionCollectionTask", task.getId(), result.getId());
        }
        return null;
    }
}
