package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionReportContentQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException.require;

/** Formal result rows contain source references; complete UMC facts remain in the original report snapshot. */
@Service
@RequiredArgsConstructor
public class InspectionUmcFormalResults {
    private final InspectionReportRevisionMapper reports;
    private final InspectionExecutionMapper executions;
    public record Observation(InspectionReportRevisionDO report, InspectionExecutionDO execution, UmcInspectionApi.Device device) { }
    public Observation observation(InspectionReportRevisionDO report) {
        require(report != null, "UMC报告不存在");
        var source = InspectionUmcReportResults.source(report);
        var execution = executions.selectById(source.executionId());
        var views = InspectionUmcReportResults.executionResults(new InspectionUmcReportResults.ExecutionResults(
                report.getTenantId(), report.getTaskId(), execution == null ? List.of() : List.of(execution), List.of(report)));
        require(views.size() == 1 && views.getFirst().result() != null, "UMC正式结果缺少已校验的执行来源");
        return new Observation(report, execution, views.getFirst().result());
    }
    public UmcInspectionApi.Rule rule(InspectionDetectionResultDO row) {
        require("UMC".equals(row.getSourceCode()) && row.getSourceReportId() != null && row.getExternalRuleId() != null,
                "UMC正式结果引用缺失");
        var report = reports.selectContent(new InspectionReportContentQuery(row.getTenantId(), row.getTaskId(), row.getSourceReportId()));
        require(report != null && Objects.equals(report.getTenantId(), row.getTenantId())
                && Objects.equals(report.getTaskId(), row.getTaskId()) && Objects.equals(report.getAttemptId(), row.getAttemptId()),
                "UMC正式结果报告归属不一致");
        var source = observation(report);
        require(Objects.equals(source.execution().getId(), row.getExecutionId()) && Objects.equals(source.execution().getDeviceId(), row.getDeviceId()),
                "UMC正式结果设备或执行不一致");
        var matches = source.device().rulelist().stream().filter(rule -> row.getExternalRuleId().equals(rule.id())).toList();
        require(matches.size() == 1, "UMC正式规则来源不唯一");
        var rule = matches.getFirst();
        require(Objects.equals(outcome(rule), row.getOutcome()), "UMC正式判定与原始结果不一致");
        return rule;
    }
    public static String outcome(UmcInspectionApi.Rule rule) {
        if (rule.support() == null || !rule.support()) return "UNJUDGED";
        if (rule.error() != null && !rule.error().isBlank()) return "ERROR";
        if (rule.result() == null) return "UNJUDGED";
        return rule.result() ? "ABNORMAL" : "PASS";
    }
    public static String severity(UmcInspectionApi.Rule rule) {
        return rule.level() == null ? "UMC_UNSPECIFIED" : "UMC_LEVEL_" + rule.level();
    }
    public static String severityName(UmcInspectionApi.Rule rule) {
        if (rule.level() == null) return "UMC未提供级别";
        return switch (rule.level()) { case 1 -> "UMC一般级"; case 2 -> "UMC重要级"; case 3 -> "UMC致命级"; default -> "UMC级别" + rule.level(); };
    }
}
