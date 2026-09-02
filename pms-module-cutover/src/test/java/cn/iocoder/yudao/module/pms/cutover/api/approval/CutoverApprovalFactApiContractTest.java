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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CutoverApprovalFactApiContractTest {

    @Test
    void exposesTheFourLockedOwnerOperations() {
        Map<String, Method> methods = Arrays.stream(CutoverApprovalFactApi.class.getDeclaredMethods())
                .collect(Collectors.toMap(Method::getName, Function.identity()));

        assertThat(methods).containsOnlyKeys("start", "inspect", "lockAndRevalidate",
                "pauseForSourceInvalidation");
        assertSignature(methods.get("start"), CutoverApprovalStartResult.class,
                CutoverApprovalStartCommand.class);
        assertSignature(methods.get("inspect"), CutoverApprovalInspectResult.class,
                CutoverApprovalFactQuery.class);
        assertSignature(methods.get("lockAndRevalidate"), CutoverApprovalRevalidationResult.class,
                CutoverApprovalRevalidationQuery.class);
        assertSignature(methods.get("pauseForSourceInvalidation"), CutoverApprovalCommandResult.class,
                CutoverApprovalPauseCommand.class);
    }

    @Test
    void carriesEveryLockedRecordFieldExactly() {
        assertThat(componentNames(CutoverApprovalStartCommand.class)).containsExactly(
                "tenantId", "taskId", "expectedTaskVersion", "planRevisionId", "planRevisionNo", "grade",
                "assessmentId", "assessmentVersion", "checklistId", "checklistVersion", "sourceSnapshotVersion",
                "planSubmittedAt", "previousApprovalInstanceId", "idempotencyKey", "correlationId");
        assertThat(componentNames(CutoverApprovalFactQuery.class)).containsExactly(
                "tenantId", "taskId", "planRevisionId");
        assertThat(componentNames(CutoverApprovalFact.class)).containsExactly(
                "approvalInstanceId", "approvalVersion", "taskId", "planRevisionId", "planRevisionNo", "status",
                "sourceSnapshotVersion", "replacementApprovalInstanceId", "decisionAt", "rejectionReason");
        assertThat(componentNames(ExpectedCutoverApprovalFact.class)).containsExactly(
                "approvalInstanceId", "approvalVersion", "taskId", "planRevisionId", "planRevisionNo", "status",
                "sourceSnapshotVersion", "replacementApprovalInstanceId", "decisionAt", "rejectionReason");
        assertThat(componentNames(CutoverApprovalRevalidationQuery.class)).containsExactly(
                "tenantId", "expected");
        assertThat(componentNames(CutoverApprovalPauseCommand.class)).containsExactly(
                "tenantId", "approvalInstanceId", "expectedApprovalVersion", "planRevisionId",
                "expectedSourceSnapshotVersion", "reasonCode", "idempotencyKey", "correlationId");
        assertThat(componentNames(CutoverApprovalStartResult.class)).containsExactly("outcome", "fact");
        assertThat(componentNames(CutoverApprovalInspectResult.class)).containsExactly("status", "fact");
        assertThat(componentNames(CutoverApprovalRevalidationResult.class)).containsExactly(
                "status", "currentFact");
        assertThat(componentNames(CutoverApprovalCommandResult.class)).containsExactly("outcome", "fact");
    }

    @Test
    void requiresTheTrustedPlanSubmissionTime() {
        assertThatThrownBy(() -> new CutoverApprovalStartCommand(
                9L, 101L, 3, 201L, 1, "A", 301L, 2, 401L, 4,
                5, null, null, "start-1", "corr-1"))
                .isInstanceOfSatisfying(CutoverApprovalFactException.class,
                        error -> assertThat(error.code()).isEqualTo(CutoverApprovalFactException.Code.INVALID_REQUEST));
    }

    @Test
    void exposesTheClosedStatusAndErrorSets() {
        assertThat(ApprovalStatus.values()).containsExactly(
                ApprovalStatus.PENDING, ApprovalStatus.PAUSED_SOURCE_INVALIDATED,
                ApprovalStatus.APPROVED, ApprovalStatus.REJECTED);
        assertThat(InspectStatus.values()).containsExactly(InspectStatus.FOUND, InspectStatus.NOT_FOUND);
        assertThat(RevalidationStatus.values()).containsExactly(RevalidationStatus.VALID, RevalidationStatus.STALE);
        assertThat(StartOutcome.values()).containsExactly(StartOutcome.STARTED, StartOutcome.REPLAYED);
        assertThat(CommandOutcome.values()).containsExactly(CommandOutcome.APPLIED, CommandOutcome.REPLAYED);
        assertThat(CutoverApprovalFactException.Code.values()).containsExactly(
                CutoverApprovalFactException.Code.INVALID_REQUEST,
                CutoverApprovalFactException.Code.TENANT_CONTEXT_MISMATCH,
                CutoverApprovalFactException.Code.NOT_FOUND,
                CutoverApprovalFactException.Code.STATE_CONFLICT,
                CutoverApprovalFactException.Code.VERSION_CONFLICT,
                CutoverApprovalFactException.Code.IDEMPOTENCY_CONFLICT,
                CutoverApprovalFactException.Code.IDEMPOTENCY_IN_PROGRESS,
                CutoverApprovalFactException.Code.OWNER_DATA_CORRUPTED,
                CutoverApprovalFactException.Code.PROVIDER_UNAVAILABLE);
    }

    @Test
    void controlledOwnerCompletesStartInspectRevalidateAndPauseLoop() {
        ControlledCutoverApprovalFactApi api = new ControlledCutoverApprovalFactApi();
        CutoverApprovalStartCommand start = new CutoverApprovalStartCommand(
                9L, 101L, 3, 201L, 1, "A", 301L, 2, 401L, 4,
                5, java.time.LocalDateTime.of(2026, 9, 3, 18, 0), null, "start-1", "corr-1");

        CutoverApprovalStartResult started = api.start(start);
        assertThat(started.outcome()).isEqualTo(StartOutcome.STARTED);
        assertThat(started.fact().status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(api.start(start).outcome()).isEqualTo(StartOutcome.REPLAYED);
        assertThat(api.inspect(new CutoverApprovalFactQuery(9L, 101L, 201L)).fact())
                .isEqualTo(started.fact());

        ExpectedCutoverApprovalFact expected = expected(started.fact());
        assertThat(api.lockAndRevalidate(new CutoverApprovalRevalidationQuery(9L, expected)).status())
                .isEqualTo(RevalidationStatus.VALID);

        CutoverApprovalCommandResult paused = api.pauseForSourceInvalidation(
                new CutoverApprovalPauseCommand(9L, started.fact().approvalInstanceId(),
                        started.fact().approvalVersion(), 201L, 5, "SOURCE_FACT_INVALIDATED",
                        "pause-1", "corr-2"));
        assertThat(paused.outcome()).isEqualTo(CommandOutcome.APPLIED);
        assertThat(paused.fact().status()).isEqualTo(ApprovalStatus.PAUSED_SOURCE_INVALIDATED);
    }

    @Test
    void controlledOwnerCompletesDecisionAndReplacementLoop() {
        ControlledCutoverApprovalFactApi api = new ControlledCutoverApprovalFactApi();
        CutoverApprovalFact first = api.start(new CutoverApprovalStartCommand(
                9L, 101L, 3, 201L, 1, "D", 301L, 2, null, null,
                5, java.time.LocalDateTime.of(2026, 9, 3, 18, 0), null, "start-1", "corr-1")).fact();
        CutoverApprovalFact rejected = api.reject(first.approvalInstanceId(), 1000L, "revise plan");

        CutoverApprovalFact replacement = api.start(new CutoverApprovalStartCommand(
                9L, 101L, 4, 202L, 2, "D", 301L, 2, null, null,
                6, java.time.LocalDateTime.of(2026, 9, 4, 18, 0), rejected.approvalInstanceId(),
                "start-2", "corr-2")).fact();
        CutoverApprovalFact approved = api.approve(replacement.approvalInstanceId(), 2000L);

        assertThat(api.inspect(new CutoverApprovalFactQuery(9L, 101L, 201L)).fact()
                .replacementApprovalInstanceId()).isEqualTo(replacement.approvalInstanceId());
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    private static ExpectedCutoverApprovalFact expected(CutoverApprovalFact fact) {
        return new ExpectedCutoverApprovalFact(fact.approvalInstanceId(), fact.approvalVersion(), fact.taskId(),
                fact.planRevisionId(), fact.planRevisionNo(), fact.status(), fact.sourceSnapshotVersion(),
                fact.replacementApprovalInstanceId(), fact.decisionAt(), fact.rejectionReason());
    }

    private static String[] componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toArray(String[]::new);
    }

    private static void assertSignature(Method method, Class<?> returnType, Class<?> parameterType) {
        assertThat(method.getReturnType()).isEqualTo(returnType);
        assertThat(method.getParameterTypes()).containsExactly(parameterType);
    }
}
