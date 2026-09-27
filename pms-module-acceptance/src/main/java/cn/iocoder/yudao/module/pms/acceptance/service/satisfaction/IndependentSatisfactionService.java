package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionTemplateRevisionQuery;
import cn.iocoder.yudao.module.pms.acceptance.domain.satisfaction.SatisfactionQuestionnaireDefinition;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Independent ACC collection freezes a published questionnaire, without inventing a project task. */
@Service @RequiredArgsConstructor
public class IndependentSatisfactionService {
    private final SatisfactionCollectionTaskMapper tasks;
    private final SatisfactionQuestionnaireMapper questionnaires;
    private final SatisfactionQuestionnaireTemplateRevisionMapper revisions;
    private final ProjectAcceptanceContextApi projects;
    private final PlatformCommandExecutionApi commands;
    private final PermissionApi permissions;

    public static boolean direct(SatisfactionCollectionTaskDO task) {
        return task != null && "DIRECT".equals(task.getOriginKind());
    }

    public ProjectAcceptanceContextApi.Context context(Long tenant, Long actor, Long projectId) {
        requireActor(tenant, actor);
        return projects.inspect(new ProjectAcceptanceContextApi.Query(tenant, projectId, actor));
    }

    public Created create(Long tenant, Long actor, Create command, String key) {
        requireActor(tenant, actor);
        if (command == null || command.projectId() == null || command.templateId() == null
                || command.templateRevisionId() == null || command.expectedProjectVersion() == null
                || command.expectedTreeVersion() == null || key == null || key.isBlank() || key.length() > 128)
            throw new IllegalArgumentException("SATISFACTION_CREATE_INVALID");
        context(tenant, actor, command.projectId());
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenant,
                        "ACC_INDEPENDENT_SATISFACTION_CREATE", actor, key),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(command)), Created.class,
                () -> createOnce(tenant, actor, command, key), created -> new PlatformCommandExecutionApi.SuccessFacts(
                        "INDEPENDENT_SATISFACTION_CREATED", "SatisfactionCollectionTask", created.taskId().toString(),
                        key, JsonUtils.toJsonString(created), List.of()));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT)
            throw new IllegalStateException("SATISFACTION_CREATE_IDEMPOTENCY_CONFLICT");
        if (result.response() == null || result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS)
            throw new IllegalStateException("SATISFACTION_CREATE_IN_PROGRESS");
        return result.response();
    }

    private Created createOnce(Long tenant, Long actor, Create command, String key) {
        var project = projects.lock(new ProjectAcceptanceContextApi.Query(tenant, command.projectId(), actor),
                command.expectedProjectVersion(), command.expectedTreeVersion());
        if (!"ACTIVE".equals(project.lifecycleStatus())) throw new IllegalStateException("SATISFACTION_PROJECT_NOT_ACTIVE");
        var revision = revisions.selectFrozenRevision(new SatisfactionTemplateRevisionQuery(tenant,
                command.templateId(), command.templateRevisionId()));
        if (revision == null || !tenant.equals(revision.getTenantId()) || !"PUBLISHED".equals(revision.getRevisionStatus()))
            throw new IllegalStateException("SATISFACTION_PUBLISHED_QUESTIONNAIRE_REQUIRED");
        var definition = SatisfactionQuestionnaireDefinition.parse(revision.getFrozenQuestionJson());
        if (revision.getFrozenThreshold() == null || !Objects.equals(revision.getRuleVersion(), definition.ruleVersion())
                || revision.getFrozenThreshold().compareTo(definition.threshold()) != 0)
            throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_PROJECTION_CONFLICT");
        var task = new SatisfactionCollectionTaskDO();
        task.setId(IdWorker.getId()); task.setTenantId(tenant); task.setProjectId(project.projectId());
        task.setOriginKind("DIRECT"); task.setOriginKey(actor + ":" + key);
        task.setOriginSnapshot(JsonUtils.toJsonString(Map.of("projectVersion", project.projectVersion(),
                "treeVersion", project.treeVersion(), "actorId", actor, "templateRevisionId", revision.getId())));
        task.setSourceOwnerContext("ACC"); task.setSourceObjectType("SatisfactionCollectionTask");
        task.setSourceObjectId(task.getId().toString()); task.setSourceObjectVersion(1L);
        task.setTriggerOwnerContext("ACC"); task.setTriggerObjectType("IndependentSatisfactionRequest");
        task.setTriggerFactId(task.getOriginKey()); task.setTriggerFactVersion(1L);
        task.setCollectionKey("SAT-" + task.getId()); task.setTaskRevisionNo(1);
        task.setAssignedToUserId(actor); task.setAssignedByUserId(actor);
        task.setTaskStatus("PENDING_COLLECTION"); task.setQuestionnaireId(IdWorker.getId()); task.setVersion(0L);
        task.setCreator(actor.toString()); task.setUpdater(actor.toString());
        var questionnaire = new SatisfactionQuestionnaireDO(); questionnaire.setId(task.getQuestionnaireId());
        questionnaire.setTenantId(tenant); questionnaire.setCollectionTaskId(task.getId());
        questionnaire.setTemplateId(revision.getTemplateId()); questionnaire.setTemplateRevisionId(revision.getId());
        questionnaire.setTemplateVersion(revision.getRevisionNo()); questionnaire.setFrozenQuestionJson(revision.getFrozenQuestionJson());
        questionnaire.setFrozenThreshold(revision.getFrozenThreshold()); questionnaire.setRuleVersion(revision.getRuleVersion());
        questionnaire.setQuestionnaireStatus("ACTIVE"); questionnaire.setAccessScopeVersion(project.treeVersion()); questionnaire.setVersion(0L);
        questionnaire.setCreator(actor.toString()); questionnaire.setUpdater(actor.toString());
        if (tasks.insert(task) != 1 || questionnaires.insert(questionnaire) != 1)
            throw new IllegalStateException("SATISFACTION_CREATE_WRITE_CONFLICT");
        return new Created(task.getId(), questionnaire.getId(), task.getCollectionKey());
    }

    /** Call before taking ACC write locks; direct collections still require a live editable project. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockIfDirect(Long tenant, Long actor, SatisfactionCollectionTaskDO task) {
        if (!direct(task)) return;
        if (!Objects.equals(tenant, task.getTenantId()) || task.getProjectTaskId() != null || task.getDeliverableId() != null)
            throw new IllegalStateException("SATISFACTION_DIRECT_IDENTITY_INVALID");
        var query = new ProjectAcceptanceContextApi.Query(tenant, task.getProjectId(), actor);
        var current = projects.inspect(query);
        var locked = projects.lock(query, current.projectVersion(), current.treeVersion());
        if (!"ACTIVE".equals(locked.lifecycleStatus())) throw new IllegalStateException("SATISFACTION_PROJECT_NOT_ACTIVE");
    }

    private void requireActor(Long tenant, Long actor) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || actor == null || actor <= 0
                || !permissions.hasAnyPermissions(actor, "pms:acceptance:satisfaction:manage"))
            throw new IllegalStateException("SATISFACTION_MANAGE_FORBIDDEN");
    }
    public record Create(Long projectId, Long templateId, Long templateRevisionId, Long expectedProjectVersion, Long expectedTreeVersion) { }
    public record Created(Long taskId, Long questionnaireId, String collectionKey) { }
}
