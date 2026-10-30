package cn.iocoder.yudao.module.bpm.api.task;

import java.util.List;
import java.util.Set;

/** 仅接收BPM已授权分页内的流程身份；业务Owner提供冻结项目名称，不授予额外读取权限。 */
public interface BpmProjectSummaryProvider {
    List<ProjectSummary> getProjectSummaries(Query query);

    record Query(Long tenantId, Set<String> authorizedProcessInstanceIds) {
        public Query { authorizedProcessInstanceIds = Set.copyOf(authorizedProcessInstanceIds); }
    }
    record ProjectSummary(String processInstanceId, String projectName) { }
}
