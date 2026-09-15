package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.RelativeTimeCondition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** One anchor mapping shared by evaluation, registration and delivery. Never searches previous rounds. */
@Component
@RequiredArgsConstructor
public class ProjectRelativeTimeFacts {
    private final ProjectNodeExecutionMapper executions;
    public record Boundary(Long executionId, Instant dueAt) { }

    public RuleFact resolve(Long tenant, Long project, Long execution, JsonNode parameters) {
        var boundary = boundary(parameters, execution, executions.selectCurrent(new ProjectPlanScopeQuery(tenant, project)));
        return boundary == null ? RuleFact.unknown("WAIT_ANCHOR_UNAVAILABLE")
                : RuleFact.known(!Instant.now().isBefore(boundary.dueAt()));
    }

    public static Boundary boundary(JsonNode parameters, Long execution, List<ProjectNodeExecutionDO> current) {
        boolean activated = RelativeTimeCondition.anchor(parameters) == RelativeTimeCondition.Anchor.NODE_ACTIVATED;
        var matches = current.stream().filter(round -> activated
                ? execution != null && execution.equals(round.getId())
                : Objects.equals(parameters.path("sourceNodeKey").asText(), round.getNodeKey())).toList();
        if (matches.size() != 1) return null;
        var source = matches.getFirst();
        if (!activated && !"DONE".equals(source.getStatus())) return null;
        var at = activated ? source.getAdmittedAt() : source.getEndedAt();
        if (at == null) return null;
        return new Boundary(source.getId(), RelativeTimeCondition.deadline(parameters, at.atZone(ZoneId.systemDefault()).toInstant()));
    }
}
