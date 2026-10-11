package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** Rework and binding repair commit together before either operation's AFTER_COMMIT reevaluation. */
@Service
@RequiredArgsConstructor
public class ProjectPlanReworkActivationService {
    private final ProjectPlanDraftService drafts;
    private final ProjectReworkService reworks;
    private final ProjectPlanActivationService activation;
    private final PlatformCommandExecutionApi commands;
    private final PermissionApi permissions;

    public record Apply(Long projectId, Long draftId, ProjectPlanDraftService.Preview expectedPreview,
                        ProjectReworkService.Apply rework) { }
    public record Applied(ProjectPlanActivationService.Applied plan, ProjectReworkService.Result rework) { }

    @Transactional(rollbackFor = Exception.class)
    public Applied apply(Apply command, Long actorId, String key) {
        if (command == null || command.expectedPreview() == null || command.rework() == null
                || !Objects.equals(command.projectId(), command.rework().projectId())
                || !Objects.equals(command.draftId(), command.expectedPreview().draftId())
                || key == null || key.isBlank() || key.length() > 128
                || command.rework().selectedNodeKeys() == null || command.rework().selectedNodeKeys().isEmpty())
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var scope = drafts.authorize(command.projectId(), actorId);
        // Replay still checks both permissions; each underlying command enforces its own scope again.
        if (!permissions.hasAnyPermissions(actorId, ProjectReworkService.PERMISSION)) throw exception(PROJECT_REWORK_FORBIDDEN);
        String correlation = "PROJECT_PLAN_REWORK_APPLY:" + command.projectId() + ":" + key;
        String innerKey = "plan-rework:" + DigestUtil.sha256Hex(correlation);
        var outcome = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(scope.tenantId(),
                        "PROJECT_PLAN_REWORK_APPLY", actorId, key), DigestUtil.sha256Hex(JsonUtils.toJsonString(command)),
                Applied.class, () -> {
                    var observed = drafts.prepare(command.projectId(), command.draftId(),
                            command.expectedPreview().draftVersion(), actorId, true).preview();
                    if (!Objects.equals(observed, command.expectedPreview())
                            || !Objects.equals(observed.basePlanVersionId(), command.rework().planVersionId())
                            || !Objects.equals(observed.projectVersion(), command.rework().expectedProjectVersion()))
                        throw exception(PROJECT_PLAN_VERSION_CONFLICT);
                    validateRepair(observed, command.rework().selectedNodeKeys());
                    var reworked = reworks.apply(command.rework(), actorId, innerKey);
                    // Never weaken the compiler or immutable-history guards: validate the actual new round.
                    var repaired = drafts.preview(command.projectId(), command.draftId(), observed.draftVersion(), actorId);
                    if (!repaired.issues().isEmpty()) throw exception(PROJECT_PLAN_CHANGE_INVALID);
                    var applied = activation.apply(new ProjectPlanActivationService.Apply(command.projectId(),
                            command.draftId(), repaired), actorId, innerKey);
                    return new Applied(applied, reworked);
                }, applied -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_PLAN_REWORK_APPLY", "Project",
                        command.projectId().toString(), correlation, JsonUtils.toJsonString(Map.of(
                        "planVersionId", applied.plan().planVersionId(), "selectedNodeKeys", command.rework().selectedNodeKeys())), List.of()));
        if (outcome.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (outcome.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return outcome.response();
    }

    private void validateRepair(ProjectPlanDraftService.Preview preview, List<String> selected) {
        if (preview.changes().stream().noneMatch(change -> "UPDATE".equals(change.action()))
                || preview.changes().stream().anyMatch(change -> !"REEVALUATE".equals(change.action())
                    && !("UPDATE".equals(change.action()) && selected.contains(change.nodeKey())))
                || !preview.deliverableChanges().isEmpty() || !preview.milestoneChanges().isEmpty() || !preview.gateChanges().isEmpty()
                || preview.issues().stream().anyMatch(issue -> !"STARTED_BINDING_IMMUTABLE".equals(issue.code())
                    || selected.stream().noneMatch(node -> ("nodes." + node + ".binding").equals(issue.field()))))
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
    }
}
