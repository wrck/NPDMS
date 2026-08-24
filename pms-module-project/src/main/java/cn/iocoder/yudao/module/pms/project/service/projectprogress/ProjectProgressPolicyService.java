package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressPolicyItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressPolicyRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectprogress.ProjectProgressRules;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectOperationAuditService;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.CreateProgressPolicyCommand;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PROGRESS_APPROVAL_NOT_CONFIGURED;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PROGRESS_POLICY_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PROGRESS_POLICY_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PROGRESS_POLICY_STATUS_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PROGRESS_POLICY_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_PROJECTION_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class ProjectProgressPolicyService {
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_APPROVING = "APPROVING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final ProjectProgressPolicyRevisionMapper revisionMapper;
    private final ProjectProgressPolicyItemMapper itemMapper;
    private final ProjectTreeScopeService scopeService;
    private final BpmProcessInstanceApi processInstanceApi;
    private final ProjectProgressProperties properties;
    private final ProjectOperationAuditService auditService;
    private final ProjectProgressMetrics metrics;

    @Transactional(rollbackFor = Exception.class)
    public Long createRevision(CreateProgressPolicyCommand command, Actor actor) {
        validate(command, actor);
        ProjectMasterDO parent = requireParentForUpdate(command.parentProjectId(), actor.tenantId());
        ProjectTreeVersionDO treeVersion = requireActiveTree(parent);
        scopeService.assertFullAccess(actor.actorId(), parent.getId(), treeVersion.getTreeVersion());
        List<Long> childIds = projectMapper.selectChildren(parent.getId()).stream()
                .map(ProjectMasterDO::getId).toList();
        List<CreateProgressPolicyCommand.Item> normalized = normalize(command.policyType(), childIds, command.items());
        ProjectProgressPolicyRevisionDO active = revisionMapper.selectActiveByParentForUpdate(parent.getId());
        ProjectProgressPolicyRevisionDO latest = revisionMapper.selectLatestByParentForUpdate(parent.getId());
        ProjectProgressPolicyRevisionDO revision = insertRevision(parent.getId(), command.policyType(), STATUS_DRAFT,
                latest == null ? 1 : latest.getRevisionNo() + 1, active == null ? null : active.getId(), null);
        insertItems(revision.getId(), normalized);
        audit(actor, revision, "PROJECT_PROGRESS_POLICY_CREATE", "SUCCESS",
                Map.of("parentProjectId", parent.getId(), "revisionNo", revision.getRevisionNo(),
                        "policyType", revision.getPolicyType(), "itemCount", normalized.size()));
        return revision.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public String submitForApproval(Long revisionId, Integer expectedVersion, Actor actor) {
        if (revisionId == null || expectedVersion == null || actor == null || actor.tenantId() == null
                || actor.actorId() == null || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(PROJECT_PROGRESS_POLICY_INVALID, "提交参数缺失");
        }
        ProjectProgressPolicyRevisionDO revision = revisionMapper.selectByIdForUpdate(revisionId);
        if (revision == null || !Objects.equals(revision.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_PROGRESS_POLICY_NOT_EXISTS);
        }
        if (!STATUS_DRAFT.equals(revision.getStatus())) throw exception(PROJECT_PROGRESS_POLICY_STATUS_INVALID);
        if (!Objects.equals(revision.getVersion(), expectedVersion)) {
            throw exception(PROJECT_PROGRESS_POLICY_VERSION_CONFLICT);
        }
        ProjectMasterDO parent = requireParentForUpdate(revision.getParentProjectId(), actor.tenantId());
        ProjectTreeVersionDO treeVersion = requireActiveTree(parent);
        scopeService.assertFullAccess(actor.actorId(), parent.getId(), treeVersion.getTreeVersion());
        String processKey = properties.getProcessDefinitionKey();
        if (processKey == null || processKey.isBlank()) throw exception(PROJECT_PROGRESS_APPROVAL_NOT_CONFIGURED);
        BpmProcessInstanceCreateReqDTO request = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(processKey)
                .setBusinessKey(String.valueOf(revision.getId()))
                .setVariables(Map.of("policyRevisionId", revision.getId(),
                        "parentProjectId", revision.getParentProjectId(), "revisionNo", revision.getRevisionNo()));
        String processInstanceId = processInstanceApi.createProcessInstance(actor.actorId(), request);
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalStateException("项目进度策略审批流程未返回实例编号");
        }
        revision.setStatus(STATUS_APPROVING);
        revision.setProcessDefinitionKey(processKey);
        revision.setProcessInstanceId(processInstanceId);
        revision.setVersion(revision.getVersion() + 1);
        revisionMapper.updateById(revision);
        audit(actor, revision, "PROJECT_PROGRESS_POLICY_SUBMIT", "SUCCESS",
                Map.of("processInstanceId", processInstanceId, "revisionNo", revision.getRevisionNo()));
        return processInstanceId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void onApprovalResult(String processInstanceId, Integer status, String reason) {
        if (processInstanceId == null || status == null) {
            metrics.approvalCallback("invalid");
            return;
        }
        ProjectProgressPolicyRevisionDO revision = revisionMapper.selectByProcessInstanceIdForUpdate(processInstanceId);
        if (revision == null) {
            metrics.approvalCallback("unmatched");
            return;
        }
        if (!STATUS_APPROVING.equals(revision.getStatus())) {
            metrics.approvalCallback("duplicate_or_out_of_order");
            return;
        }
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            activate(revision);
            metrics.approvalCallback("approved");
        } else if (Objects.equals(status, BpmProcessInstanceStatusEnum.REJECT.getStatus())
                || Objects.equals(status, BpmProcessInstanceStatusEnum.CANCEL.getStatus())) {
            revision.setStatus(STATUS_REJECTED);
            revision.setVersion(revision.getVersion() + 1);
            revisionMapper.updateById(revision);
            metrics.approvalCallback("rejected");
        } else {
            metrics.approvalCallback("out_of_order");
            return;
        }
        Actor systemActor = new Actor(revision.getTenantId(), 0L, processInstanceId);
        audit(systemActor, revision, "PROJECT_PROGRESS_POLICY_CALLBACK", "SUCCESS",
                Map.of("status", status, "reason", reason == null ? "" : reason));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectProgressPolicyRevisionDO requireActiveOrCreateDefault(Long parentProjectId, Actor actor) {
        if (parentProjectId == null || actor == null || actor.tenantId() == null
                || actor.actorId() == null || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(PROJECT_PROGRESS_POLICY_INVALID, "默认策略参数缺失");
        }
        ProjectProgressPolicyRevisionDO active = revisionMapper.selectActiveByParent(parentProjectId);
        if (active != null) return active;
        ProjectMasterDO parent = requireParentForUpdate(parentProjectId, actor.tenantId());
        active = revisionMapper.selectActiveByParentForUpdate(parentProjectId);
        if (active != null) return active;
        ProjectTreeVersionDO treeVersion = requireActiveTree(parent);
        scopeService.assertFullAccess(actor.actorId(), parent.getId(), treeVersion.getTreeVersion());
        List<Long> childIds = projectMapper.selectChildren(parentProjectId).stream().map(ProjectMasterDO::getId).toList();
        List<CreateProgressPolicyCommand.Item> items = normalize(
                ProjectProgressRules.POLICY_SYSTEM_EQUAL, childIds, List.of());
        ProjectProgressPolicyRevisionDO latest = revisionMapper.selectLatestByParentForUpdate(parentProjectId);
        LocalDateTime now = LocalDateTime.now();
        ProjectProgressPolicyRevisionDO revision = insertRevision(parentProjectId,
                ProjectProgressRules.POLICY_SYSTEM_EQUAL, STATUS_ACTIVE,
                latest == null ? 1 : latest.getRevisionNo() + 1, null, now);
        insertItems(revision.getId(), items);
        audit(actor, revision, "PROJECT_PROGRESS_POLICY_DEFAULT", "SUCCESS",
                Map.of("parentProjectId", parentProjectId, "itemCount", items.size()));
        return revision;
    }

    public List<ProjectProgressPolicyRevisionDO> listByParent(Long parentProjectId, Actor actor) {
        ProjectMasterDO parent = projectMapper.selectById(parentProjectId);
        if (parent == null || !Objects.equals(parent.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectTreeVersionDO treeVersion = requireActiveTree(parent);
        scopeService.assertFullAccess(actor.actorId(), parent.getId(), treeVersion.getTreeVersion());
        return revisionMapper.selectListByParent(parentProjectId);
    }

    public Map<Long, List<ProjectProgressPolicyItemDO>> listItemsByRevisionIds(List<Long> revisionIds) {
        return itemMapper.selectByRevisionIds(revisionIds).stream().collect(
                Collectors.groupingBy(ProjectProgressPolicyItemDO::getPolicyRevisionId));
    }

    private void activate(ProjectProgressPolicyRevisionDO revision) {
        LocalDateTime now = LocalDateTime.now();
        ProjectProgressPolicyRevisionDO active = revisionMapper.selectActiveByParentForUpdate(revision.getParentProjectId());
        if (active != null && !active.getId().equals(revision.getId())) {
            revision.setSupersedesRevisionId(active.getId());
            active.setStatus(STATUS_SUPERSEDED);
            active.setEffectiveTo(now);
            active.setVersion(active.getVersion() + 1);
            revisionMapper.updateById(active);
        }
        revision.setStatus(STATUS_ACTIVE);
        revision.setEffectiveFrom(now);
        revision.setApprovedAt(now);
        revision.setVersion(revision.getVersion() + 1);
        revisionMapper.updateById(revision);
    }

    private ProjectProgressPolicyRevisionDO insertRevision(Long parentProjectId, String policyType, String status,
                                                            int revisionNo, Long supersedesId,
                                                            LocalDateTime effectiveFrom) {
        ProjectProgressPolicyRevisionDO revision = new ProjectProgressPolicyRevisionDO();
        revision.setParentProjectId(parentProjectId);
        revision.setRevisionNo(revisionNo);
        revision.setStatus(status);
        revision.setPolicyType(policyType);
        revision.setSupersedesRevisionId(supersedesId);
        revision.setEffectiveFrom(effectiveFrom);
        revision.setVersion(0);
        if (revisionMapper.insert(revision) != 1) {
            throw new IllegalStateException("项目进度策略版本写入失败");
        }
        return revision;
    }

    private void insertItems(Long revisionId, List<CreateProgressPolicyCommand.Item> items) {
        List<ProjectProgressPolicyItemDO> rows = items.stream().map(item -> {
            ProjectProgressPolicyItemDO row = new ProjectProgressPolicyItemDO();
            row.setPolicyRevisionId(revisionId);
            row.setChildProjectId(item.childProjectId());
            row.setWeight(item.weight());
            row.setIncludeStatusSnapshot(JsonUtils.toJsonString(item.includeStatuses()));
            row.setVersion(0);
            return row;
        }).toList();
        if (!Boolean.TRUE.equals(itemMapper.insertBatch(rows))) {
            throw new IllegalStateException("项目进度策略项写入失败");
        }
    }

    private List<CreateProgressPolicyCommand.Item> normalize(String policyType, List<Long> childIds,
                                                               List<CreateProgressPolicyCommand.Item> items) {
        try {
            return ProjectProgressRules.normalize(policyType, childIds, items);
        } catch (IllegalArgumentException failure) {
            throw exception(PROJECT_PROGRESS_POLICY_INVALID, failure.getMessage());
        }
    }

    private ProjectMasterDO requireParentForUpdate(Long projectId, Long tenantId) {
        ProjectMasterDO parent = projectMapper.selectByIdForUpdate(projectId);
        if (parent == null || !Objects.equals(parent.getTenantId(), tenantId)) throw exception(PROJECT_NOT_EXISTS);
        return parent;
    }

    private ProjectTreeVersionDO requireActiveTree(ProjectMasterDO project) {
        Long rootId = project.getRootId() == null ? project.getId() : project.getRootId();
        ProjectTreeVersionDO active = treeVersionMapper.selectLatestActive(rootId);
        if (active == null) throw exception(PROJECT_TREE_PROJECTION_UNAVAILABLE);
        return active;
    }

    private void validate(CreateProgressPolicyCommand command, Actor actor) {
        if (command == null || command.parentProjectId() == null || command.policyType() == null
                || actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(PROJECT_PROGRESS_POLICY_INVALID, "创建参数缺失");
        }
    }

    private void audit(Actor actor, ProjectProgressPolicyRevisionDO revision, String operation,
                       String result, Map<String, ?> detail) {
        auditService.record(revision.getTenantId() == null ? actor.tenantId() : revision.getTenantId(),
                actor.actorId(), actor.correlationId(), operation, "ProgressPolicyRevision",
                String.valueOf(revision.getId()), result, detail);
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {}
}
