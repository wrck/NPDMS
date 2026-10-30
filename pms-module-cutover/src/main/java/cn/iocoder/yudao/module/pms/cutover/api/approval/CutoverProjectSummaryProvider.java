package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProjectSummaryProvider;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalWorkflowMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.CutoverApprovalProjectSummaryQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CutoverProjectSummaryProvider implements BpmProjectSummaryProvider {
    private final CutoverApprovalWorkflowMapper workflows;

    @Override
    public List<ProjectSummary> getProjectSummaries(Query query) {
        if (TenantContextHolder.isIgnore() || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("流程摘要租户不一致");
        }
        if (query.authorizedProcessInstanceIds().isEmpty()) return List.of();
        return workflows.selectProjectSummaries(new CutoverApprovalProjectSummaryQuery(
                        query.tenantId(), query.authorizedProcessInstanceIds())).stream()
                .map(row -> new ProjectSummary(row.getProcessInstanceId(), row.getProjectName())).toList();
    }
}
