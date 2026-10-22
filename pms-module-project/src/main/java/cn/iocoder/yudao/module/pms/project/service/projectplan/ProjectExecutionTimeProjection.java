package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultEvidenceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.ProjectAssignmentCompletionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.ProjectAssignmentCompletionQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectBusinessResultSources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.*;

/** Read-only display times. Never writes submitted_at or substitutes current facts for missing historical evidence. */
@Component
@RequiredArgsConstructor
public class ProjectExecutionTimeProjection {
    private final ResultEvidenceMapper evidence;
    private final ProjectAssignmentCompletionMapper members;
    private final ProjectBusinessResultSources sources;
    private final List<TaskBusinessObjectProvider> owners;

    public record Time(LocalDateTime at, String source, String label) { }
    public record Query(Long tenantId, Long projectId, Long actorId, ProjectNodeExecutionDO round,
                        TemplateExecutionSnapshot snapshot, List<ProjectNodeExecutionDO> rounds) { }

    public Time project(Query query) { return project(query, new HashSet<>()); }

    private Time project(Query query, Set<Long> visited) {
        var round = query.round();
        if (!Objects.equals(query.tenantId(), round.getTenantId()) || !Objects.equals(query.projectId(), round.getProjectId()))
            return null;
        if (round.getSubmittedAt() != null) return new Time(round.getSubmittedAt(), "MANUAL", "手工提交");
        if (!"DONE".equals(round.getStatus()) || round.getEndedAt() == null || query.snapshot() == null
                || !visited.add(round.getId())) return null;
        try {
            var recorded = round.getResultSnapshot() == null ? null : JsonUtils.parseTree(round.getResultSnapshot());
            if (recorded == null || !recorded.isObject()) return null;
            var subscribed = subscriptionTime(query, recorded);
            if (subscribed != null) return subscribed;
            var member = memberTime(query, recorded);
            if (member != null) return member;
            var linked = linkedTime(query, recorded);
            if (linked != null) return linked;
            // Native stage completion is defined by its required tasks, using only this frozen plan/round boundary.
            if ("STAGE".equals(round.getNodeKind())) {
                var stage = query.snapshot().getStages().stream().filter(s -> Objects.equals(s.getNodeKey(), round.getNodeKey())).findFirst().orElse(null);
                var program = stage == null ? null : query.snapshot().getRulePrograms().get(stage.getCompletionRuleKey());
                if (program != null && program.leaves().stream().anyMatch(l -> "STAGE_NATIVE_STATUS".equals(l.predicate()))) {
                    var times = new ArrayList<Time>();
                    for (var task : query.snapshot().getTasks()) {
                        if (!Objects.equals(stage.getCode(), task.getStageCode()) || Boolean.FALSE.equals(task.getRequired())) continue;
                        var candidates = query.rounds().stream().filter(r -> "TASK".equals(r.getNodeKind())
                                && Objects.equals(round.getPlanVersionId(), r.getPlanVersionId()) && Objects.equals(task.getNodeKey(), r.getNodeKey())
                                && "DONE".equals(r.getStatus()) && r.getEndedAt() != null && !r.getEndedAt().isAfter(round.getEndedAt())).toList();
                        var child = candidates.stream().max(Comparator.comparing(ProjectNodeExecutionDO::getRoundNo)).orElse(null);
                        if (child == null) return null;
                        var time = project(new Query(query.tenantId(), query.projectId(), query.actorId(), child, query.snapshot(), query.rounds()), visited);
                        if (time == null) return null;
                        times.add(time);
                    }
                    return latest(times, "BUSINESS_END", "必需任务的业务完成时间");
                }
            }
            return null;
        } catch (IllegalArgumentException invalidEvidence) {
            return null;
        } finally { visited.remove(round.getId()); }
    }

    private Time subscriptionTime(Query query, JsonNode recorded) {
        var receipts = recorded.path("subscriptionEvidence");
        if (!receipts.isArray() || receipts.isEmpty()) return null;
        var times = new ArrayList<Time>();
        for (var receipt : receipts) {
            if (receipt.path("executionId").asLong() != query.round().getId()
                    || receipt.path("planVersionId").asLong() != query.round().getPlanVersionId()) return null;
            var scan = evidence.selectById(new ResultEvidenceMapper.ScanId(query.tenantId(), query.projectId(), receipt.path("scanId").asLong()));
            if (scan == null || !Objects.equals(scan.getTenantId(), query.tenantId()) || !Objects.equals(scan.getProjectId(), query.projectId())
                    || !Objects.equals(scan.getSubscriptionId(), receipt.path("subscriptionId").asLong())
                    || !Objects.equals(scan.getSubscriptionVersion(), receipt.path("subscriptionVersion").asInt())
                    || !Objects.equals(scan.getThroughSequence(), receipt.path("throughSequence").asLong())
                    || !"SATISFIED".equals(scan.getStatus())) return null;
            long cursor = 0;
            boolean found = false;
            while (true) {
                var items = evidence.selectItems(new ResultEvidenceMapper.Items(query.tenantId(), query.projectId(), receipt.path("scanId").asLong(), cursor, 100));
                for (var item : items) {
                    if (!Objects.equals(item.getTenantId(), query.tenantId()) || !Objects.equals(item.getProjectId(), query.projectId())
                            || !Objects.equals(item.getScanId(), receipt.path("scanId").asLong())) return null;
                    if (!"ELIGIBLE".equals(item.getEligibility())) continue;
                    var observation = JsonUtils.parseObject(item.getObservation(), BusinessResultSource.Observation.class);
                    var result = observation.result();
                    if (result == null || !Objects.equals(result.tenantId(), query.tenantId()) || !Objects.equals(result.projectId(), query.projectId())
                            || !Objects.equals(result.objectId(), item.getObjectId()) || !Objects.equals(result.resultId(), item.getResultId())
                            || !validTime(query, result.formedAt())) return null;
                    found = true;
                    times.add(new Time(result.formedAt(), "BUSINESS_END", result.type().resultType()));
                }
                if (items.size() < 100) break;
                long next = items.getLast().getId();
                if (next <= cursor) return null;
                cursor = next;
            }
            if (!found) return null;
        }
        return latest(times, "BUSINESS_END", "冻结业务结果形成时间");
    }

    private Time memberTime(Query query, JsonNode recorded) {
        var endUsers = recorded.path("projectEndUsers");
        var ids = new HashSet<Long>();
        if (endUsers.path("projectId").asLong() == query.projectId()) {
            for (var member : endUsers.path("members")) ids.add(member.path("assignmentId").asLong());
        } else if (assignmentEvidence(recorded)) {
            for (var fact : recorded.path("businessFacts").path("results")) {
                if (!fact.path("facts").path("PROJECT_ASSIGNMENT_COMPLETED").asBoolean()) continue;
                String version = fact.path("factVersion").asText();
                // Owner's frozen version carries the exact member interval IDs; never select today's members by role.
                if (!version.startsWith("PROJECT_ASSIGNMENT:v1:")) return null;
                String payload = version.substring("PROJECT_ASSIGNMENT:v1:".length());
                int begin = payload.indexOf(':'); int end = payload.lastIndexOf(":true");
                if (begin < 0 || end <= begin) return null;
                for (String pair : payload.substring(begin + 1, end).split(",")) ids.add(Long.valueOf(pair.split(":")[0]));
            }
        }
        if (ids.isEmpty()) return null;
        var rows = members.members(new ProjectAssignmentCompletionQuery(query.tenantId(), query.projectId(), false));
        var times = new ArrayList<Time>();
        boolean endUser = endUsers.isObject();
        for (Long id : ids) {
            var row = rows.stream().filter(r -> Objects.equals(r.getId(), id) && Objects.equals(r.getTenantId(), query.tenantId())
                    && Objects.equals(r.getProjectId(), query.projectId())).findFirst().orElse(null);
            if (row == null) return null;
            if (endUser ? !ProjectMemberRoles.END_USER.equals(row.getMemberRole()) :
                    !ProjectMemberRoles.isServiceManager(row.getMemberRole()) && !ProjectMemberRoles.PROJECT_MANAGER.equals(row.getMemberRole())) continue;
            if (!validTime(query, row.getEffectiveFrom())) return null;
            times.add(new Time(row.getEffectiveFrom(), "BUSINESS_START", "成员指派生效时间"));
        }
        if (endUser) return times.stream().map(Time::at).min(Comparator.naturalOrder())
                .map(t -> new Time(t, "BUSINESS_START", "最终用户加入生效时间")).orElse(null);
        return latest(times, "BUSINESS_START", "人员指派生效时间");
    }

    private boolean assignmentEvidence(JsonNode recorded) {
        if ("PROJ".equals(recorded.path("ownerContext").asText()) && "PROJECT_ASSIGNMENT".equals(recorded.path("objectType").asText())) return true;
        for (var result : recorded.path("businessResults"))
            if ("PROJ".equals(result.path("ownerContext").asText()) && "PROJECT_ASSIGNMENT".equals(result.path("objectType").asText())) return true;
        return false;
    }

    private Time linkedTime(Query query, JsonNode recorded) {
        String owner = recorded.path("ownerContext").asText();
        String entity = recorded.path("objectType").asText();
        if (owner.isBlank() && "STAGE".equals(query.round().getNodeKind())) {
            var stage = query.snapshot().getStages().stream().filter(s -> Objects.equals(s.getNodeKey(), query.round().getNodeKey())).findFirst().orElse(null);
            if (stage != null && stage.getBinding() != null) { owner = stage.getBinding().getTargetContextCode(); entity = stage.getBinding().getTargetObjectType(); }
        }
        String ownerCode = owner, entityCode = entity;
        var providers = owners.stream().filter(o -> Objects.equals(ownerCode, o.ownerContext()) && Objects.equals(entityCode, o.objectType())).toList();
        if (providers.size() != 1) return null;
        var times = new ArrayList<Time>();
        var usedFacts = new HashSet<String>();
        var ruleKeys = new ArrayList<String>();
        query.snapshot().getTasks().stream().filter(n -> "TASK".equals(query.round().getNodeKind()) && Objects.equals(n.getNodeKey(),query.round().getNodeKey()))
                .forEach(n -> { ruleKeys.add(n.getCompletionRuleKey()); ruleKeys.add(n.getExitRuleKey()); });
        query.snapshot().getStages().stream().filter(n -> "STAGE".equals(query.round().getNodeKind()) && Objects.equals(n.getNodeKey(),query.round().getNodeKey()))
                .forEach(n -> { ruleKeys.add(n.getCompletionRuleKey()); ruleKeys.add(n.getExitRuleKey()); });
        for (String key : ruleKeys) {
            if (key == null) continue;
            var program = query.snapshot().getRulePrograms().get(key);
            if (program != null) program.leaves().stream().filter(l -> "BUSINESS_FACT".equals(l.predicate()) && !l.parameters().has("sourceNodeKey"))
                    .forEach(l -> usedFacts.add(l.parameters().path("factCode").asText()));
        }
        for (var frozen : recorded.path("businessFacts").path("results")) {
            try {
                var context = new TaskBusinessObjectProvider.Context(query.tenantId(), query.actorId(), query.projectId(), "TASK".equals(query.round().getNodeKind()) ? query.round().getNodeInstanceId() : null, "execution-history");
                String expected = frozen.path("factVersion").asText();
                String suffix = ":execution:" + query.round().getId();
                if (expected.endsWith(suffix)) expected = expected.substring(0, expected.length() - suffix.length());
                var satisfied = new HashSet<String>();
                frozen.path("facts").properties().forEach(entry -> { if (entry.getValue().asBoolean()) satisfied.add(entry.getKey()); });
                satisfied.retainAll(usedFacts);
                if (satisfied.isEmpty()) return null;
                var nativeTime = providers.getFirst().historicalHandlingTime(new TaskBusinessObjectProvider.HandlingTimeQuery(context,
                        frozen.path("objectId").asText(), expected, satisfied));
                if (nativeTime != null) {
                    if (!validTime(query, nativeTime.at())) return null;
                    times.add(new Time(nativeTime.at(), nativeTime.source(), nativeTime.label()));
                    continue;
                }
                if (context.taskId() == null) return null;
                var current = providers.getFirst().inspect(context, frozen.path("objectId").asText());
                if (!Objects.equals(expected, current.factVersion())) return null;
                boolean found = false;
                for (var type : sources.descriptors()) {
                    if (!Objects.equals(ownerCode, type.type().ownerContext()) || !Objects.equals(entityCode, type.type().entityType())
                            || !satisfied.contains(type.type().resultType()) || !type.currentLookup()) continue;
                    var result = sources.inspect(new BusinessResultSource.Query(query.tenantId(), query.projectId(), type.type(), current.objectId(), null)).result();
                    if (result == null || !validTime(query, result.formedAt())) return null;
                    times.add(new Time(result.formedAt(), "BUSINESS_END", type.type().resultType()));
                    found = true;
                }
                if (!found) return null;
            } catch (RuntimeException unavailable) { return null; }
        }
        return times.size() == 1 ? times.getFirst() : latest(times, "BUSINESS_END", "对应业务完成时间");
    }

    private boolean validTime(Query query, LocalDateTime time) { return time != null && !time.isAfter(query.round().getEndedAt()); }
    private Time latest(List<Time> times, String source, String label) {
        return times.stream().map(Time::at).max(Comparator.naturalOrder()).map(t -> new Time(t, source, label)).orElse(null);
    }
}
