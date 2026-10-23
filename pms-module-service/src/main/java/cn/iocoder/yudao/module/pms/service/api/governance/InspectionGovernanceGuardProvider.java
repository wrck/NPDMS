package cn.iocoder.yudao.module.pms.service.api.governance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.ServiceIssueMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionProjectRows;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** PM-10 only reads SRV facts; it never advances inspection or project lifecycle. */
@Component
@RequiredArgsConstructor
public class InspectionGovernanceGuardProvider implements ProjectGovernanceGuardProviderApi {
    public static final String PROVIDER_CODE = "INSPECTION";
    private static final String FACT_VERSION = "INSPECTION_TASK_ISSUE_V2";
    private static final Set<String> TERMINAL_TASKS = Set.of("CLOSED", "ARCHIVED", "CANCELLED");
    private final InspectionTaskMapper taskMapper;
    private final ServiceIssueMapper issueMapper;

    @Override public String providerCode() { return PROVIDER_CODE; }

    @Override public ProjectGovernanceProviderFact inspect(ProjectGovernanceGuardQuery query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("query tenant must match trusted tenant context");
        var facts = new ArrayList<String>(); var blockers = new ArrayList<ProjectGovernanceBlocker>();
        if (!query.projectIds().isEmpty()) {
            var scope = new InspectionProjectRows(query.tenantId(), query.projectIds());
            for (var task : taskMapper.selectForGovernance(scope)) {
                validateScope(new ScopedFact(query, task.getTenantId(), task.getProjectId()));
                facts.add("TASK|" + task.getProjectId() + "|" + task.getId() + "|" + task.getStatusCode() + "|" + task.getVersion() + "|" + task.getUpdateTime());
                if (task.getStatusCode() == null || !TERMINAL_TASKS.contains(task.getStatusCode()))
                    blockers.add(new ProjectGovernanceBlocker("INSPECTION_TASK", task.getId().toString(), status(task.getStatusCode()),
                            "NON_TERMINAL_INSPECTION_TASK", "巡检任务尚未完成处置"));
            }
            for (var issue : issueMapper.selectForGovernance(scope)) {
                validateScope(new ScopedFact(query, issue.getTenantId(), issue.getProjectId()));
                facts.add("ISSUE|" + issue.getProjectId() + "|" + issue.getTaskId() + "|" + issue.getId() + "|" + issue.getStatusCode() + "|" + issue.getVersion() + "|" + issue.getUpdateTime());
                if (!"CLOSED".equals(issue.getStatusCode()))
                    blockers.add(new ProjectGovernanceBlocker("INSPECTION_ISSUE", issue.getId().toString(), status(issue.getStatusCode()),
                            "OPEN_INSPECTION_ISSUE", "巡检跟踪事项尚未确认闭环"));
            }
        }
        facts.sort(Comparator.naturalOrder());
        blockers.sort(Comparator.comparing(ProjectGovernanceBlocker::objectType).thenComparing(ProjectGovernanceBlocker::objectId));
        String digest = DigestUtil.sha256Hex(String.join("\n", facts));
        return new ProjectGovernanceProviderFact(PROVIDER_CODE, FACT_VERSION, facts.isEmpty() ? "EMPTY" : digest, digest, List.copyOf(blockers));
    }

    private record ScopedFact(ProjectGovernanceGuardQuery query, Long tenantId, Long projectId) { }
    private static void validateScope(ScopedFact fact) {
        if (!Objects.equals(fact.tenantId(), fact.query().tenantId()) || !fact.query().projectIds().contains(fact.projectId()))
            throw new IllegalStateException("inspection guard query returned out-of-scope fact");
    }
    private static String status(String code) { return code == null ? "UNKNOWN" : code; }
}
