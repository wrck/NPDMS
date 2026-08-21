package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.Decision;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.IdempotencyScope;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.SuccessFacts;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;

/** F-PROJ-001正式创建的唯一应用事务入口。 */
@Service
public class ProjectManualCreationApplicationService {

    public static final String CREATE_SCOPE = "POST:/pms/projects";

    @Resource
    private ProjectCreationPlatformFactService platformFactService;
    @Resource
    private ProjectManualCreationService projectCreationService;

    public ManualProjectCreateResult create(ManualProjectCreateCommand command, Actor actor) {
        validate(command, actor);
        var execution = platformFactService.execute(
                new IdempotencyScope(actor.tenantId(), CREATE_SCOPE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), ManualProjectCreateResult.class,
                () -> createOnce(command),
                result -> successFacts(command, actor, result));
        if (execution.decision() == Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == Decision.IN_PROGRESS) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        return execution.response();
    }

    private ManualProjectCreateResult createOnce(ManualProjectCreateCommand command) {
        ProjectMasterDO project = projectCreationService.createProject(command.draft(),
                command.orderOfficeCompanyCode(), command.orderOfficeDepartmentCode(),
                command.templateId(), command.serviceManagerUserId());
        ProjectInstantiation instances = projectCreationService.getInstances(project.getId());
        return new ManualProjectCreateResult(
                project.getId(), project.getProjectCode(), project.getStatus(), project.getLifecycleStatus(),
                project.getCurrentStage(), project.getAssignmentStatus(), project.getVersion(),
                project.getLifecycleTemplateId(), project.getLifecycleTemplateRevisionNo(),
                project.getTemplateLoadMethod(), instances.getStages().size(), instances.getTasks().size(),
                instances.getMilestones().size(), instances.getDeliverables().size(), instances.getGates().size(),
                command.serviceManagerUserId() != null);
    }

    private SuccessFacts successFacts(ManualProjectCreateCommand command, Actor actor,
                                      ManualProjectCreateResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", result.id());
        detail.put("templateId", result.lifecycleTemplateId());
        detail.put("templateRevisionNo", result.lifecycleTemplateRevisionNo());
        detail.put("creationReasonDigest", sha256(command.draft().getCreationReason()));
        detail.put("stageCount", result.stageCount());
        detail.put("taskCount", result.taskCount());
        detail.put("milestoneCount", result.milestoneCount());
        detail.put("deliverableCount", result.deliverableCount());
        detail.put("gateCount", result.gateCount());
        return new SuccessFacts("PROJECT_CREATE", "Project", String.valueOf(result.id()),
                actor.correlationId(), JsonUtils.toJsonString(detail),
                "ProjectCreated", JsonUtils.toJsonString(result));
    }

    private void validate(ManualProjectCreateCommand command, Actor actor) {
        if (command == null || command.draft() == null || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.requestDigest() == null
                || actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("正式项目创建命令不完整");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
