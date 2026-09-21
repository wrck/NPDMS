package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultEvidenceScanDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultEvidenceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultSubscriptionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultEvidencePolicy;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionContract;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 只读订阅观察摘要：复用既有扫描行与证据政策解释，等待原因只引用可关联当前订阅身份的评估记录。
 * 调用方必须先完成项目/任务访问授权并解析租户；本服务不触发扫描、安装、返工或任何业务写入。
 * 已满足只代表证据政策成立，不代表节点已完成；完成历史不回写，失效影响由原返工入口处理。
 */
@Service
@RequiredArgsConstructor
public class ProjectResultSubscriptionObservationQuery {
    private static final int REASON_LIMIT = 20;

    private final ProjectNodeExecutionMapper executions;
    private final ResultSubscriptionMapper subscriptions;
    private final ResultEvidenceMapper evidence;

    public record SubscriptionObservation(String subscriptionKey, String resultType, String waitReason,
                                          String scanStatus, boolean scanCurrent, long examined, long eligible,
                                          Set<String> missingObjects, List<String> reasons) { }

    public record RoundObservation(Long executionId, Long planVersionId, String nodeKey, String nodeKind,
                                   String roundStatus, boolean roundEnded,
                                   List<SubscriptionObservation> subscriptions) { }

    /** 指定节点的当前轮次订阅观察；无当前轮次、无订阅行或无评估记录时不推测原因。 */
    public List<RoundObservation> forNode(Long tenantId, Long projectId, String nodeKind, Long nodeId) {
        return observe(rounds(tenantId, projectId).stream()
                .filter(round -> nodeKind.equals(round.getNodeKind()) && Objects.equals(nodeId, round.getNodeInstanceId()))
                .toList(), tenantId, projectId);
    }

    /** 节点执行列表消费：只返回存在未退休订阅行的轮次，调用方须先完成项目访问授权。 */
    public Map<Long, RoundObservation> forExecutionIds(Long tenantId, Long projectId, Collection<Long> executionIds) {
        if (executionIds == null || executionIds.isEmpty()) return Map.of();
        var rounds = rounds(tenantId, projectId).stream().filter(round -> executionIds.contains(round.getId())).toList();
        var result = new LinkedHashMap<Long, RoundObservation>();
        for (RoundObservation observation : observe(rounds, tenantId, projectId))
            result.put(observation.executionId(), observation);
        return result;
    }

    private List<ProjectNodeExecutionDO> rounds(Long tenantId, Long projectId) {
        return executions.selectCurrent(new ProjectPlanScopeQuery(tenantId, projectId));
    }

    private List<RoundObservation> observe(List<ProjectNodeExecutionDO> rounds, Long tenantId, Long projectId) {
        if (rounds.isEmpty()) return List.of();
        var executionIds = rounds.stream().map(ProjectNodeExecutionDO::getId).toList();
        var rows = subscriptions.selectByExecutions(new ResultSubscriptionMapper.Executions(tenantId, projectId, executionIds));
        if (rows.isEmpty()) return List.of();
        var byExecution = new LinkedHashMap<Long, List<ResultSubscriptionDO>>();
        for (ResultSubscriptionDO row : rows)
            byExecution.computeIfAbsent(row.getExecutionId(), ignored -> new ArrayList<>()).add(row);
        var result = new ArrayList<RoundObservation>();
        for (ProjectNodeExecutionDO round : rounds) {
            var rowsForRound = byExecution.get(round.getId());
            if (rowsForRound == null) continue;
            result.add(new RoundObservation(round.getId(), round.getPlanVersionId(), round.getNodeKey(), round.getNodeKind(),
                    round.getStatus(), roundEnded(round),
                    rowsForRound.stream().map(this::observe).toList()));
        }
        return List.copyOf(result);
    }

    private boolean roundEnded(ProjectNodeExecutionDO round) {
        return round.getEndedAt() != null || Set.of("DONE", "COMPLETED", "TERMINATED").contains(round.getStatus());
    }

    private SubscriptionObservation observe(ResultSubscriptionDO row) {
        var definition = ResultSubscriptionContract.read(row.getConfiguration());
        String waitReason;
        String scanStatus = null;
        boolean scanCurrent = false;
        long examined = 0, eligible = 0;
        Set<String> missingObjects = Set.of();
        List<String> reasons = List.of();
        var scan = evidence.selectScanForSubscription(new ResultEvidenceMapper.SubscriptionScan(
                row.getTenantId(), row.getProjectId(), row.getId(), row.getVersion()));
        if (scan == null) {
            waitReason = "EVALUATION_PENDING";
        } else if (!Objects.equals(row.getVersion(), scan.getSubscriptionVersion())) {
            // 旧扫描只显示待更新，不得把过期结论显示为当前已满足。
            waitReason = "EVALUATION_OUTDATED";
        } else {
            scanCurrent = true;
            scanStatus = scan.getStatus();
            if ("COLLECTING".equals(scan.getStatus())) {
                waitReason = "EVALUATION_SCANNING";
            } else {
                var decision = decide(definition, scan);
                if (decision == null) waitReason = "EVALUATION_PENDING";
                else {
                    examined = decision.examined();
                    eligible = decision.eligible();
                    missingObjects = decision.missingObjects();
                    waitReason = switch (decision.status()) {
                        case SATISFIED -> "EVIDENCE_SATISFIED";
                        case AMBIGUOUS -> "RESULT_AMBIGUOUS";
                        case UNAVAILABLE -> "RESULT_SOURCE_UNAVAILABLE";
                        case WAITING -> decision.eligible() == 0 ? "NO_QUALIFIED_RESULT" : "EXPECTED_OBJECTS_MISSING";
                        case COLLECTING -> "EVALUATION_SCANNING";
                    };
                }
                reasons = evidence.selectDistinctReasons(new ResultEvidenceMapper.ScanReasons(
                        row.getTenantId(), row.getProjectId(), scan.getId(), REASON_LIMIT));
            }
        }
        return new SubscriptionObservation(row.getSubscriptionKey(), definition.resultType(), waitReason,
                scanStatus, scanCurrent, examined, eligible, missingObjects, reasons);
    }

    private ResultEvidencePolicy.Decision decide(cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration.Subscription definition,
                                                 ResultEvidenceScanDO scan) {
        try {
            var accumulated = JsonUtils.parseObject(scan.getAccumulator(), ResultEvidencePolicy.Accumulator.class);
            return ResultEvidencePolicy.decide(definition, accumulated, true);
        } catch (RuntimeException corrupt) {
            return null; // 评估摘要损坏时显示待评估，不构造替代结论。
        }
    }
}
