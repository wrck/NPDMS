package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.ApprovalStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CommandOutcome;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalCommandResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFact;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFactQuery;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalInspectResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalPauseCommand;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalRevalidationQuery;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalRevalidationResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartCommand;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartResult;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.ExpectedCutoverApprovalFact;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.InspectStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.RevalidationStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.StartOutcome;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 仅供CUT正向测试显式装配的确定性F-CUT-005事实实现。 */
public final class ControlledCutoverApprovalFactApi implements CutoverApprovalFactApi {

    private final Map<Long, CutoverApprovalFact> facts = new LinkedHashMap<>();
    private final Map<PlanKey, Long> planIndex = new LinkedHashMap<>();
    private final Map<IntentKey, StartEntry> starts = new LinkedHashMap<>();
    private final Map<IntentKey, PauseEntry> pauses = new LinkedHashMap<>();
    private long nextApprovalInstanceId = 70001L;

    @Override
    public CutoverApprovalStartResult start(CutoverApprovalStartCommand command) {
        IntentKey intent = new IntentKey(command.tenantId(), command.idempotencyKey());
        StartEntry replay = starts.get(intent);
        if (replay != null) {
            requireSame(replay.command(), command);
            return new CutoverApprovalStartResult(StartOutcome.REPLAYED, facts.get(replay.approvalInstanceId()));
        }
        long approvalInstanceId = nextApprovalInstanceId++;
        CutoverApprovalFact fact = new CutoverApprovalFact(
                approvalInstanceId, 0, command.taskId(), command.planRevisionId(), command.planRevisionNo(),
                ApprovalStatus.PENDING, command.sourceSnapshotVersion(), null, null, null);
        facts.put(approvalInstanceId, fact);
        planIndex.put(new PlanKey(command.tenantId(), command.taskId(), command.planRevisionId()), approvalInstanceId);
        starts.put(intent, new StartEntry(command, approvalInstanceId));
        if (command.previousApprovalInstanceId() != null) {
            CutoverApprovalFact previous = facts.get(command.previousApprovalInstanceId());
            if (previous == null) {
                throw failure(CutoverApprovalFactException.Code.NOT_FOUND, "previous approval is missing");
            }
            facts.put(previous.approvalInstanceId(), new CutoverApprovalFact(
                    previous.approvalInstanceId(), previous.approvalVersion() + 1, previous.taskId(),
                    previous.planRevisionId(), previous.planRevisionNo(), previous.status(),
                    previous.sourceSnapshotVersion(), approvalInstanceId, previous.decisionAt(),
                    previous.rejectionReason()));
        }
        return new CutoverApprovalStartResult(StartOutcome.STARTED, fact);
    }

    @Override
    public CutoverApprovalInspectResult inspect(CutoverApprovalFactQuery query) {
        Long id = planIndex.get(new PlanKey(query.tenantId(), query.taskId(), query.planRevisionId()));
        return id == null
                ? new CutoverApprovalInspectResult(InspectStatus.NOT_FOUND, null)
                : new CutoverApprovalInspectResult(InspectStatus.FOUND, facts.get(id));
    }

    @Override
    public CutoverApprovalRevalidationResult lockAndRevalidate(CutoverApprovalRevalidationQuery query) {
        CutoverApprovalFact current = facts.get(query.expected().approvalInstanceId());
        if (current == null) {
            throw failure(CutoverApprovalFactException.Code.NOT_FOUND, "approval is missing");
        }
        return new CutoverApprovalRevalidationResult(
                matches(query.expected(), current) ? RevalidationStatus.VALID : RevalidationStatus.STALE,
                current);
    }

    @Override
    public CutoverApprovalCommandResult pauseForSourceInvalidation(CutoverApprovalPauseCommand command) {
        IntentKey intent = new IntentKey(command.tenantId(), command.idempotencyKey());
        PauseEntry replay = pauses.get(intent);
        if (replay != null) {
            requireSame(replay.command(), command);
            return new CutoverApprovalCommandResult(CommandOutcome.REPLAYED,
                    facts.get(command.approvalInstanceId()));
        }
        CutoverApprovalFact current = facts.get(command.approvalInstanceId());
        if (current == null) {
            throw failure(CutoverApprovalFactException.Code.NOT_FOUND, "approval is missing");
        }
        CutoverApprovalFact paused = change(current, ApprovalStatus.PAUSED_SOURCE_INVALIDATED, null, null);
        facts.put(paused.approvalInstanceId(), paused);
        pauses.put(intent, new PauseEntry(command));
        return new CutoverApprovalCommandResult(CommandOutcome.APPLIED, paused);
    }

    public CutoverApprovalFact reject(long approvalInstanceId, long decisionAt, String reason) {
        CutoverApprovalFact rejected = change(requireFact(approvalInstanceId), ApprovalStatus.REJECTED,
                decisionAt, reason);
        facts.put(approvalInstanceId, rejected);
        return rejected;
    }

    public CutoverApprovalFact approve(long approvalInstanceId, long decisionAt) {
        CutoverApprovalFact approved = change(requireFact(approvalInstanceId), ApprovalStatus.APPROVED,
                decisionAt, null);
        facts.put(approvalInstanceId, approved);
        return approved;
    }

    private CutoverApprovalFact requireFact(long approvalInstanceId) {
        CutoverApprovalFact fact = facts.get(approvalInstanceId);
        if (fact == null) {
            throw failure(CutoverApprovalFactException.Code.NOT_FOUND, "approval is missing");
        }
        return fact;
    }

    private static CutoverApprovalFact change(CutoverApprovalFact current, ApprovalStatus status,
                                               Long decisionAt, String rejectionReason) {
        return new CutoverApprovalFact(current.approvalInstanceId(), current.approvalVersion() + 1,
                current.taskId(), current.planRevisionId(), current.planRevisionNo(), status,
                current.sourceSnapshotVersion(), current.replacementApprovalInstanceId(), decisionAt,
                rejectionReason);
    }

    private static boolean matches(ExpectedCutoverApprovalFact expected, CutoverApprovalFact current) {
        return Objects.equals(expected.approvalInstanceId(), current.approvalInstanceId())
                && Objects.equals(expected.approvalVersion(), current.approvalVersion())
                && Objects.equals(expected.taskId(), current.taskId())
                && Objects.equals(expected.planRevisionId(), current.planRevisionId())
                && Objects.equals(expected.planRevisionNo(), current.planRevisionNo())
                && expected.status() == current.status()
                && Objects.equals(expected.sourceSnapshotVersion(), current.sourceSnapshotVersion())
                && Objects.equals(expected.replacementApprovalInstanceId(), current.replacementApprovalInstanceId())
                && Objects.equals(expected.decisionAt(), current.decisionAt())
                && Objects.equals(expected.rejectionReason(), current.rejectionReason());
    }

    private static void requireSame(Object original, Object replay) {
        if (!original.equals(replay)) {
            throw failure(CutoverApprovalFactException.Code.IDEMPOTENCY_CONFLICT,
                    "idempotency payload conflicts");
        }
    }

    private static CutoverApprovalFactException failure(CutoverApprovalFactException.Code code, String message) {
        return new CutoverApprovalFactException(code, message);
    }

    private record PlanKey(Long tenantId, Long taskId, Long planRevisionId) {
    }

    private record IntentKey(Long tenantId, String idempotencyKey) {
    }

    private record StartEntry(CutoverApprovalStartCommand command, Long approvalInstanceId) {
    }

    private record PauseEntry(CutoverApprovalPauseCommand command) {
    }
}
