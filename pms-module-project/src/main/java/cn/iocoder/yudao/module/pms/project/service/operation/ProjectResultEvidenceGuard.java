package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.*;
import cn.iocoder.yudao.module.pms.project.domain.rule.ResultEvidenceReceipt;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.domain.template.ResultEvidencePolicy.Accumulator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 原完成Writer在Owner事实/规则锁之后调用，证据屏障持续到同一事务提交。 */
@Service
@RequiredArgsConstructor
public class ProjectResultEvidenceGuard {
    private final ResultSubscriptionMapper subscriptions;
    private final ResultEvidenceMapper evidence;
    private final ProjectResultEvidenceConsistency consistency;
    private final PlatformBusinessEventApi outbox;

    public record Proof(boolean ready, String reason, List<ResultEvidenceReceipt> receipts) {
        public Proof { receipts = List.copyOf(receipts); }
    }
    public static boolean configured(tools.jackson.databind.JsonNode execution) {
        return execution != null && !TemplateExecutionConfiguration.read(execution).subscriptions().isEmpty();
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Proof lock(TemplateExecutionSnapshot snapshot, ProjectPlanVersionDO plan, ProjectNodeExecutionDO round) {
        var matching = ResultSubscriptionContract.nodes(snapshot).stream().filter(node -> Objects.equals(node.kind(),round.getNodeKind())
                && Objects.equals(node.key(),round.getNodeKey())).toList();
        if (matching.isEmpty()) return new Proof(true,"NO_RESULT_SUBSCRIPTION",List.of());
        if (matching.size() != 1 || !Objects.equals(TenantContextHolder.getRequiredTenantId(),round.getTenantId())
                || !Objects.equals(plan.getTenantId(),round.getTenantId()) || !Objects.equals(plan.getProjectId(),round.getProjectId())
                || !Objects.equals(plan.getId(),round.getPlanVersionId()) || !"EFFECTIVE".equals(plan.getStatus())
                || !"ACTIVE".equals(round.getStatus()) || !Integer.valueOf(1).equals(round.getCurrentMarker()) || round.getEndedAt() != null)
            throw new IllegalStateException("RESULT_EVIDENCE_EXECUTION_MISMATCH");
        // 同一项目多个来源始终按Owner/实体/结果/局部键取得屏障，不按事件到达顺序锁定。
        var definitions = matching.getFirst().subscriptions().stream().sorted(Comparator.comparing(value -> {
            var sub=ResultSubscriptionContract.read(value);
            return sub.ownerContext()+"\u0000"+sub.entityType()+"\u0000"+sub.resultType()+"\u0000"+sub.key();
        })).toList();
        var receipts = new ArrayList<ResultEvidenceReceipt>();
        for (var frozen : definitions) {
            var definition = ResultSubscriptionContract.read(frozen);
            var row = subscriptions.selectIdentityForUpdate(new ResultSubscriptionMapper.Identity(round.getTenantId(),round.getProjectId(),plan.getId(),round.getId(),definition.key()));
            if (row == null) return waiting("RESULT_SUBSCRIPTION_NOT_INSTALLED");
            if (!Objects.equals(row.getTenantId(),round.getTenantId()) || !Objects.equals(row.getProjectId(),round.getProjectId())
                    || !Objects.equals(row.getPlanVersionId(),plan.getId()) || !Objects.equals(row.getExecutionId(),round.getId())
                    || !Objects.equals(row.getContractId(),round.getContractId()) || !Objects.equals(row.getNodeKind(),round.getNodeKind())
                    || !Objects.equals(row.getNodeKey(),round.getNodeKey()) || !Objects.equals(row.getNodeId(),round.getNodeInstanceId())
                    || !definition.key().equals(row.getSubscriptionKey()) || !frozen.equals(JsonUtils.parseTree(row.getConfiguration())))
                throw new IllegalStateException("RESULT_EVIDENCE_SUBSCRIPTION_MISMATCH");
            if ("RETIRED".equals(row.getPhase())) throw new IllegalStateException("RESULT_EVIDENCE_SUBSCRIPTION_RETIRED");
            ProjectResultSubscriptionWorker.checkpoint(row);
            if (!"LIVE".equals(row.getPhase())) return waiting("RESULT_SUBSCRIPTION_RECOVERING");
            var boundary = consistency.lock(row);
            if (boundary.sequence() < row.getProcessedSequence()) throw new IllegalStateException("RESULT_EVIDENCE_BOUNDARY_REGRESSED");
            if (boundary.sequence() > row.getProcessedSequence()) {
                var wakeup=ResultSubscriptionWakeup.forCause(row,"completion-refresh:"+row.getVersion()+":"+boundary.sequence());
                outbox.append("ResultSubscription",row.getId().toString(),new BusinessEvent(wakeup.eventId(),ResultSubscriptionWakeup.EVENT_TYPE,JsonUtils.toJsonString(wakeup)));
                return waiting("RESULT_EVIDENCE_CHANGED");
            }
            var scan=evidence.selectScanForUpdate(new ResultEvidenceMapper.ScanIdentity(row.getTenantId(),row.getProjectId(),row.getId(),row.getVersion()));
            if (scan == null || "COLLECTING".equals(scan.getStatus())) {
                if (scan != null) ProjectResultEvidenceScanner.requireScan(scan,row);
                var event=ResultEvidenceScanEvent.create(row,row.getVersion(),"completion:"+(scan==null?0:scan.getAfterCandidateId()));
                outbox.append("ResultSubscription",row.getId().toString(),new BusinessEvent(event.eventId(),ResultEvidenceScanEvent.EVENT_TYPE,JsonUtils.toJsonString(event)));
                return waiting("RESULT_EVIDENCE_COLLECTING");
            }
            ProjectResultEvidenceScanner.requireScan(scan,row);
            var decision=ResultEvidencePolicy.decide(definition,JsonUtils.parseObject(scan.getAccumulator(),Accumulator.class),true);
            if (!decision.status().name().equals(scan.getStatus())) throw new IllegalStateException("RESULT_EVIDENCE_CONCLUSION_MISMATCH");
            if (!decision.satisfied()) return waiting("RESULT_EVIDENCE_"+decision.status().name());
            receipts.add(new ResultEvidenceReceipt(row.getId(),row.getSubscriptionKey(),scan.getId(),row.getPlanVersionId(),row.getExecutionId(),
                    row.getContractId(),row.getVersion(),row.getBaselineSequence(),scan.getThroughSequence()));
        }
        return new Proof(true,"RESULT_EVIDENCE_SATISFIED",receipts);
    }
    private Proof waiting(String reason) { return new Proof(false,reason,List.of()); }
}
