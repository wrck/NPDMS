package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeSliceDTO;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectCompanyDepartmentRelationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitScopeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectCompanyDepartmentRelationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitRequestMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitScopeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectsplit.ProjectSplitRules;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitDraftCommand;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_MANAGE;

@Service
@RequiredArgsConstructor
public class ProjectSplitDraftService {
    private final ProjectSplitRequestMapper requestMapper;
    private final ProjectSplitItemMapper itemMapper;
    private final ProjectSplitScopeMapper scopeMapper;
    private final ProjectMasterMapper projectMapper;
    private final ProjectCompanyDepartmentRelationMapper organizationMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;
    private final DeliveryScopeApi deliveryScopeApi;
    private final OrganizationScopeApi organizationScopeApi;
    private final ProjectSplitRules rules;
    private final OperationAuditApi auditService;
    private final ProjectTreeScopeService treeScopeService;

    @Transactional(rollbackFor = Exception.class)
    public DraftResult saveDraft(ProjectSplitDraftCommand command, Actor actor) {
        validateActor(actor);
        List<String> errors = rules.validate(command);
        if (!errors.isEmpty()) {
            throw exception(PROJECT_SPLIT_DRAFT_INVALID, String.join(",", errors));
        }
        ProjectMasterDO parent = requireParent(command.parentProjectId(), actor);
        requireProjectScope(parent.getId(), actor.actorId());
        Long rootId = parent.getRootId() == null ? parent.getId() : parent.getRootId();
        ProjectTreeVersionDO activeTree = treeVersionMapper.selectLatestActive(rootId);
        if (activeTree == null) throw exception(PROJECT_TREE_PROJECTION_UNAVAILABLE);
        assertManageScope(actor, parent.getId(), activeTree.getTreeVersion());

        ProjectSplitRequestDO request;
        long scopeVersion;
        if (command.requestId() == null) {
            scopeVersion = resolveScopeVersion(parent.getId(), 0L);
            request = new ProjectSplitRequestDO();
            request.setParentProjectId(parent.getId());
            request.setStatus("DRAFT");
            request.setDraftVersion(0);
            request.setParentVersion(parent.getVersion());
            request.setScopeVersion(scopeVersion);
            request.setTreeVersion(activeTree == null ? 0L : activeTree.getTreeVersion());
            request.setTemplateRevisionId(command.templateRevisionId());
            request.setVersion(0);
            requestMapper.insert(request);
        } else {
            request = requireRequest(command.requestId(), actor);
            scopeVersion = resolveScopeVersion(parent.getId(), request.getScopeVersion());
            if (!Objects.equals(request.getParentProjectId(), command.parentProjectId())
                    || command.expectedDraftVersion() == null
                    || requestMapper.updateDraftIfMatch(request.getId(), command.expectedDraftVersion(),
                    command.templateRevisionId(), parent.getVersion(), scopeVersion,
                    activeTree == null ? 0L : activeTree.getTreeVersion()) != 1) {
                throw exception(PROJECT_SPLIT_DRAFT_VERSION_CONFLICT);
            }
            scopeMapper.physicallyDeleteByRequestId(actor.tenantId(), request.getId());
            itemMapper.physicallyDeleteByRequestId(actor.tenantId(), request.getId());
            request.setDraftVersion(command.expectedDraftVersion() + 1);
            request.setParentVersion(parent.getVersion());
            request.setScopeVersion(scopeVersion);
            request.setTreeVersion(activeTree == null ? 0L : activeTree.getTreeVersion());
        }
        persistItems(request.getId(), scopeVersion, command.items());
        auditService.record(actor.tenantId(), actor.actorId(), actor.correlationId(), "PROJECT_SPLIT_DRAFT_SAVE",
                request.getId(), "SUCCESS", Map.of("itemCount", command.items().size(),
                        "draftVersion", request.getDraftVersion()));
        return loadDraft(request);
    }

    private long resolveScopeVersion(Long parentProjectId, Long fallbackVersion) {
        try {
            return deliveryScopeApi.getAvailableSlices(parentProjectId, null).stream()
                    .map(DeliveryScopeSliceDTO::scopeVersion).filter(Objects::nonNull)
                    .max(Long::compareTo).orElse(fallbackVersion == null ? 0L : fallbackVersion);
        } catch (RuntimeException ignored) {
            // 权威范围暂不可用时仍允许保存草稿，预览和确认阶段必须重新校验。
            return fallbackVersion == null ? 0L : fallbackVersion;
        }
    }

    @Transactional(readOnly = true)
    public DraftResult getDraft(Long requestId, Actor actor) {
        validateActor(actor);
        ProjectSplitRequestDO request = requireRequest(requestId, actor);
        requireProjectScope(request.getParentProjectId(), actor.actorId());
        assertManageScope(actor, request.getParentProjectId(), request.getTreeVersion());
        return loadDraft(request);
    }

    private DraftResult loadDraft(ProjectSplitRequestDO request) {
        List<ProjectSplitItemDO> items = itemMapper.selectByRequestId(request.getId());
        List<ProjectSplitScopeDO> scopes = scopeMapper.selectByItemIds(items.stream().map(ProjectSplitItemDO::getId).toList());
        return new DraftResult(request, items, scopes);
    }

    private void persistItems(Long requestId, Long scopeVersion, List<ProjectSplitDraftCommand.Item> items) {
        for (ProjectSplitDraftCommand.Item source : items) {
            ProjectSplitItemDO item = new ProjectSplitItemDO();
            item.setSplitRequestId(requestId);
            item.setClientItemKey(source.clientItemKey());
            item.setProjectName(source.projectName());
            item.setBusinessLevelCode(source.businessLevelCode());
            item.setTreeSort(source.treeSort() == null ? 0 : source.treeSort());
            item.setOfficeDepartmentCode(source.officeDepartmentCode());
            item.setItemStatus("DRAFT");
            item.setVersion(0);
            itemMapper.insert(item);
            for (ProjectSplitDraftCommand.Scope sourceScope : source.scopes()) {
                List<String> serials = sourceScope.serialNumbers() == null ? List.of() : sourceScope.serialNumbers().stream()
                        .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).toList();
                if (serials.isEmpty()) {
                    insertScope(item.getId(), sourceScope.orderLineId(), sourceScope.quantity(),
                            sourceScope.officeDepartmentCode(), null, scopeVersion);
                } else {
                    serials.forEach(serial -> insertScope(item.getId(), sourceScope.orderLineId(), BigDecimal.ONE,
                            sourceScope.officeDepartmentCode(), serial, scopeVersion));
                }
            }
        }
    }

    private void insertScope(Long itemId, Long orderLineId, BigDecimal quantity, String officeCode,
                             String serial, Long scopeVersion) {
        ProjectSplitScopeDO scope = new ProjectSplitScopeDO();
        scope.setSplitItemId(itemId);
        scope.setOrderLineId(orderLineId);
        scope.setAllocatedQty(quantity);
        scope.setOfficeDepartmentCode(officeCode);
        scope.setSerialNo(serial);
        scope.setSourceScopeVersion(scopeVersion);
        scope.setVersion(0);
        scopeMapper.insert(scope);
    }

    private ProjectMasterDO requireParent(Long projectId, Actor actor) {
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return project;
    }

    private ProjectSplitRequestDO requireRequest(Long requestId, Actor actor) {
        ProjectSplitRequestDO request = requestMapper.selectById(requestId);
        if (request == null || !Objects.equals(request.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_SPLIT_REQUEST_NOT_EXISTS);
        }
        return request;
    }

    private void requireProjectScope(Long projectId, Long actorId) {
        ProjectCompanyDepartmentRelationDO relation = organizationMapper.selectPrimaryOrderOffice(projectId);
        if (relation == null || !organizationScopeApi.hasScope(actorId, relation.getCompanyId(), relation.getDepartmentId())) {
            throw exception(PROJECT_SPLIT_SCOPE_FORBIDDEN);
        }
    }

    private void assertManageScope(Actor actor, Long projectId, Long treeVersion) {
        treeScopeService.assertFullAccess(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ACTION_MANAGE, treeVersion));
    }

    private void validateActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("项目拆分操作人不完整");
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {}
    public record DraftResult(ProjectSplitRequestDO request, List<ProjectSplitItemDO> items,
                              List<ProjectSplitScopeDO> scopes) {}
}
