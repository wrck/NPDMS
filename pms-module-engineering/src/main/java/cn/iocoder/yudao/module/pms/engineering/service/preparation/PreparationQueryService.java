package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationCursorPageRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationFormRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationItemRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationReadinessSnapshotRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationReadinessSnapshotDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationReadinessSnapshotMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.DynamicFormItemListQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationCurrentQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationSnapshotPageQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_WORK_BINDING_NOT_AVAILABLE;

@Service
@RequiredArgsConstructor
public class PreparationQueryService {

    public static final String PERMISSION_QUERY = "pms:preparation-survey:query";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PreparationMapper preparationMapper;
    private final PreparationItemMapper itemMapper;
    private final DynamicFormInstanceMapper formMapper;
    private final PreparationReadinessSnapshotMapper snapshotMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;

    public PreparationRespVO getCurrent(Long projectId, String type, Actor actor) {
        requireQuery(actor, projectId);
        if (!"PRE_02".equals(type) && !PreparationInitializationService.PREPARATION_TYPE.equals(type)) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
        PreparationDO current = preparationMapper.selectCurrent(new PreparationCurrentQuery(
                actor.tenantId(), projectId, PreparationInitializationService.PREPARATION_TYPE));
        if (current == null) {
            requireBinding(projectId);
            return null;
        }
        return toPreparation(current);
    }

    public PreparationRespVO getDetail(Long preparationId, Actor actor) {
        requireActor(actor);
        PreparationDO row = preparationMapper.selectById(new PreparationRowQuery(
                actor.tenantId(), positive(preparationId)));
        requireVisible(row, actor);
        return toPreparation(row);
    }

    public PreparationCursorPageRespVO<PreparationItemRespVO> getItems(
            Long preparationId, PreparationPageReqVO request, Actor actor) {
        requireActor(actor);
        PreparationDO preparation = preparationMapper.selectById(new PreparationRowQuery(
                actor.tenantId(), positive(preparationId)));
        requireVisible(preparation, actor);
        ItemCursor cursor = parseItemCursor(request == null ? null : request.getCursor());
        int size = pageSize(request == null ? null : request.getPageSize());
        List<PreparationItemDO> fetched = itemMapper.selectPage(new PreparationItemPageQuery(
                actor.tenantId(), preparation.getId(), cursor.sortOrder(), cursor.itemCode(), cursor.id(), size + 1));
        boolean hasMore = fetched.size() > size;
        List<PreparationItemDO> page = hasMore ? fetched.subList(0, size) : fetched;
        Map<Long, DynamicFormInstanceDO> forms = page.isEmpty() ? Map.of()
                : formMapper.selectListByItemIds(new DynamicFormItemListQuery(actor.tenantId(), preparation.getId(),
                        page.stream().map(PreparationItemDO::getId).toList())).stream()
                .collect(Collectors.toMap(DynamicFormInstanceDO::getItemId, Function.identity()));
        List<PreparationItemRespVO> items = page.stream().map(item -> toItem(item, forms.get(item.getId()))).toList();
        return new PreparationCursorPageRespVO<>(items,
                hasMore ? itemCursor(page.getLast()) : null, hasMore);
    }

    public PreparationCursorPageRespVO<PreparationRespVO> getHistory(
            Long projectId, PreparationPageReqVO request, Actor actor) {
        requireQuery(actor, projectId);
        HistoryCursor cursor = parseHistoryCursor(request == null ? null : request.getCursor());
        int size = pageSize(request == null ? null : request.getPageSize());
        List<PreparationDO> fetched = preparationMapper.selectPage(new PreparationPageQuery(
                actor.tenantId(), projectId, PreparationInitializationService.PREPARATION_TYPE,
                cursor.businessVersion(), cursor.id(), size + 1));
        boolean hasMore = fetched.size() > size;
        List<PreparationDO> page = hasMore ? fetched.subList(0, size) : fetched;
        return new PreparationCursorPageRespVO<>(page.stream().map(this::toPreparation).toList(),
                hasMore ? historyCursor(page.getLast()) : null, hasMore);
    }

    public PreparationCursorPageRespVO<PreparationReadinessSnapshotRespVO> getReadinessSnapshots(
            Long preparationId, PreparationPageReqVO request, Actor actor) {
        requireActor(actor);
        PreparationDO preparation = preparationMapper.selectById(new PreparationRowQuery(
                actor.tenantId(), positive(preparationId)));
        requireVisible(preparation, actor);
        SnapshotCursor cursor = parseSnapshotCursor(request == null ? null : request.getCursor());
        int size = pageSize(request == null ? null : request.getPageSize());
        List<PreparationReadinessSnapshotDO> fetched = snapshotMapper.selectPage(new PreparationSnapshotPageQuery(
                actor.tenantId(), preparation.getId(), cursor.snapshotNo(), cursor.id(), size + 1));
        boolean hasMore = fetched.size() > size;
        List<PreparationReadinessSnapshotDO> page = hasMore ? fetched.subList(0, size) : fetched;
        return new PreparationCursorPageRespVO<>(page.stream().map(this::toReadinessSnapshot).toList(),
                hasMore ? snapshotCursor(page.getLast()) : null, hasMore);
    }

    private void requireBinding(Long projectId) {
        try {
            workBindingFactApi.inspect(new ProjectWorkBindingFactQuery(projectId));
        } catch (RuntimeException failure) {
            throw exception(PREPARATION_WORK_BINDING_NOT_AVAILABLE);
        }
    }

    private void requireVisible(PreparationDO row, Actor actor) {
        if (row == null) throw exception(PREPARATION_NOT_EXISTS);
        requireQuery(actor, row.getProjectId());
    }

    private void requireQuery(Actor actor, Long projectId) {
        requireActor(actor);
        long checkedProjectId = positive(projectId);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_QUERY,
                PreparationInitializationService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), checkedProjectId, ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(checkedProjectId)) {
            throw exception(FORBIDDEN);
        }
    }

    private PreparationRespVO toPreparation(PreparationDO row) {
        PreparationRespVO response = new PreparationRespVO();
        response.setPreparationId(row.getId());
        response.setProjectId(row.getProjectId());
        response.setPreparationType("PRE_02");
        response.setBusinessVersion(row.getBusinessVersion());
        response.setCurrent(Integer.valueOf(1).equals(row.getCurrentMarker()));
        response.setTemplateId(row.getTemplateId());
        response.setTemplateRevisionId(row.getTemplateRevisionId());
        response.setFixedFormCatalogVersion(row.getFixedFormCatalogVersion());
        response.setStatus(row.getStatusCode());
        response.setReadinessStatus(row.getReadinessStatusCode());
        response.setLatestReadinessSnapshotId(row.getLatestReadinessSnapshotId());
        response.setInputVersion(row.getInputVersion());
        response.setReadinessVersion(row.getReadinessVersion());
        response.setSnapshotCurrent(row.getSnapshotCurrent());
        response.setSubmittedAt(row.getSubmittedAt());
        response.setConfirmedAt(row.getConfirmedAt());
        response.setReturnedAt(row.getReturnedAt());
        response.setReturnReason(row.getReturnReason());
        response.setVersion(row.getVersion());
        response.setCreatedAt(row.getCreateTime());
        response.setAllowedActions(List.of());
        return response;
    }

    private PreparationItemRespVO toItem(PreparationItemDO row, DynamicFormInstanceDO form) {
        if (form == null) throw exception(PREPARATION_NOT_EXISTS);
        PreparationItemRespVO response = new PreparationItemRespVO();
        response.setItemId(row.getId());
        response.setItemCode(row.getItemCode());
        response.setItemName(row.getItemName());
        response.setSortOrder(row.getSortOrder());
        response.setApplicability(row.getApplicabilityCode());
        response.setConfirmationStatus(row.getConfirmationStatusCode());
        response.setOutsourced(row.getOutsourced());
        response.setAssigneeUserId(row.getAssigneeUserId());
        response.setAssigneeEffectiveFrom(row.getAssigneeEffectiveFrom());
        response.setSiteResultCode(row.getSiteResultCode());
        response.setSiteResultDetail(row.getSiteResultDetail());
        response.setEvidenceReferenceSnapshot(row.getEvidenceReferenceSnapshot());
        response.setEvidencePolicySnapshot(row.getEvidencePolicySnapshot());
        response.setSourcePolicySnapshot(row.getSourcePolicySnapshot());
        response.setWaiverPolicySnapshot(row.getWaiverPolicySnapshot());
        response.setVersion(row.getVersion());
        response.setForm(toForm(form));
        return response;
    }

    private PreparationFormRespVO toForm(DynamicFormInstanceDO row) {
        PreparationFormRespVO response = new PreparationFormRespVO();
        response.setFormInstanceId(row.getId());
        response.setFormCode(row.getFormCode());
        response.setFormVersion(row.getFormVersion());
        response.setSchemaSnapshot(row.getSchemaSnapshot());
        response.setValueSnapshot(row.getValueSnapshot());
        response.setStatus(row.getStatusCode());
        response.setFrozenAt(row.getFrozenAt());
        response.setFrozenBy(row.getFrozenBy());
        response.setVersion(row.getVersion());
        return response;
    }

    private PreparationReadinessSnapshotRespVO toReadinessSnapshot(PreparationReadinessSnapshotDO row) {
        PreparationReadinessSnapshotRespVO response = new PreparationReadinessSnapshotRespVO();
        response.setSnapshotId(row.getId());
        response.setSnapshotNo(row.getSnapshotNo());
        response.setResult(row.getResultCode());
        response.setRuleVersion(row.getRuleVersion());
        response.setProjectScopeVersion(row.getProjectScopeVersion());
        response.setInputVersion(row.getInputVersion());
        response.setPreparationVersion(row.getPreparationVersion());
        response.setReadinessVersion(row.getReadinessVersion());
        response.setItemFacts(row.getItemFactsSnapshot());
        response.setFileFacts(row.getFileFactsSnapshot());
        response.setSourceFacts(row.getSourceFactsSnapshot());
        response.setWaiverFacts(row.getWaiverFactsSnapshot());
        response.setBlockers(row.getBlockersSnapshot());
        response.setEvaluatedBy(row.getEvaluatedBy());
        response.setEvaluatedAt(row.getEvaluatedAt());
        return response;
    }

    private ItemCursor parseItemCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new ItemCursor(null, null, null);
        String[] parts = cursor.split("\\|", -1);
        try {
            if (parts.length != 3 || parts[1].isBlank()) throw new IllegalArgumentException();
            int sortOrder = Integer.parseInt(parts[0]);
            long id = Long.parseLong(parts[2]);
            if (sortOrder < 0 || id <= 0) throw new IllegalArgumentException();
            return new ItemCursor(sortOrder, parts[1], id);
        } catch (IllegalArgumentException failure) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
    }

    private HistoryCursor parseHistoryCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new HistoryCursor(null, null);
        String[] parts = cursor.split(":", -1);
        try {
            if (parts.length != 2) throw new IllegalArgumentException();
            int businessVersion = Integer.parseInt(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (businessVersion <= 0 || id <= 0) throw new IllegalArgumentException();
            return new HistoryCursor(businessVersion, id);
        } catch (IllegalArgumentException failure) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
    }

    private SnapshotCursor parseSnapshotCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new SnapshotCursor(null, null);
        String[] parts = cursor.split(":", -1);
        try {
            if (parts.length != 2) throw new IllegalArgumentException();
            int snapshotNo = Integer.parseInt(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (snapshotNo <= 0 || id <= 0) throw new IllegalArgumentException();
            return new SnapshotCursor(snapshotNo, id);
        } catch (IllegalArgumentException failure) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
    }

    private String itemCursor(PreparationItemDO row) {
        return row.getSortOrder() + "|" + row.getItemCode() + "|" + row.getId();
    }

    private String historyCursor(PreparationDO row) {
        return row.getBusinessVersion() + ":" + row.getId();
    }

    private String snapshotCursor(PreparationReadinessSnapshotDO row) {
        return row.getSnapshotNo() + ":" + row.getId();
    }

    private int pageSize(Integer requested) {
        if (requested == null) return DEFAULT_PAGE_SIZE;
        if (requested < 1 || requested > MAX_PAGE_SIZE) throw exception(PREPARATION_COMMAND_INVALID);
        return requested;
    }

    private long positive(Long value) {
        if (value == null || value <= 0) throw exception(PREPARATION_COMMAND_INVALID);
        return value;
    }

    private void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0) throw exception(FORBIDDEN);
    }

    private record ItemCursor(Integer sortOrder, String itemCode, Long id) {
    }

    private record HistoryCursor(Integer businessVersion, Long id) {
    }

    private record SnapshotCursor(Integer snapshotNo, Long id) {
    }

    public record Actor(Long tenantId, Long actorId) {
    }
}
