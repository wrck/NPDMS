package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemLineageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemObjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationRowQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PreparationFilePolicyProvider implements FileBusinessObjectPolicyProvider {

    public static final String OWNER_CONTEXT = "SOL";
    public static final String OBJECT_TYPE = "SITE_SURVEY_ITEM";
    public static final String PURPOSE_CODE = "SITE_SURVEY_EVIDENCE";
    private static final long MAX_SIZE_BYTES = 52_428_800L;
    private static final Set<String> MEDIA_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> MUTATING_ACTIONS = Set.of(
            FileActionCodes.UPLOAD, FileActionCodes.REPLACE,
            FileActionCodes.REFERENCE, FileActionCodes.DETACH);
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            FileActionCodes.UPLOAD, FileActionCodes.REPLACE, FileActionCodes.REFERENCE,
            FileActionCodes.DETACH, FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);

    private final PreparationMapper preparationMapper;
    private final PreparationItemMapper itemMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;

    @Override public String ownerContext() { return OWNER_CONTEXT; }
    @Override public String objectType() { return OBJECT_TYPE; }

    @Override
    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return inspect(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.requiredAction());
    }

    @Override
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        return inspect(query.tenantId(), query.actorUserId(), query.key().objectId(),
                query.key().purposeCode(), query.requiredAction());
    }

    private FileBusinessObjectPolicyFact inspect(Long tenantId, Long actorUserId, String objectId,
                                                 String purposeCode, String requiredAction) {
        Context context = locate(tenantId, objectId, false);
        if (context == null || !PURPOSE_CODE.equals(purposeCode)) return denied();
        ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorUserId, context.preparation().getProjectId(),
                ProjectScopeApi.ACTION_VIEW));
        if (!inScope(scope, context.preparation().getProjectId())) return denied();
        return policy(allowed(actorUserId, requiredAction, context), scope.treeVersion(), context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return lockAndRevalidate(query.tenantId(), query.actorUserId(), query.objectId(), query.purposeCode(),
                query.requiredAction(), query.expectedScopeVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        return lockAndRevalidate(query.tenantId(), query.actorUserId(), query.key().objectId(),
                query.key().purposeCode(), query.requiredAction(), query.expectedScopeVersion());
    }

    private FileBusinessObjectPolicyFact lockAndRevalidate(Long tenantId, Long actorUserId,
                                                            String objectId, String purposeCode,
                                                            String requiredAction,
                                                            Long expectedScopeVersion) {
        Context located = locate(tenantId, objectId, false);
        if (located == null || !PURPOSE_CODE.equals(purposeCode)) return denied();
        ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                tenantId, actorUserId, located.preparation().getProjectId(),
                ProjectScopeApi.ACTION_VIEW, expectedScopeVersion));
        if (!inScope(scope, located.preparation().getProjectId())) return denied();
        Context locked = locate(tenantId, objectId, true);
        if (locked == null || !Objects.equals(locked.preparation().getProjectId(),
                located.preparation().getProjectId())) return denied();
        return policy(allowed(actorUserId, requiredAction, locked), scope.treeVersion(), locked);
    }

    private boolean allowed(Long actorId, String action, Context context) {
        if (!SUPPORTED_ACTIONS.contains(action)) return false;
        if (MUTATING_ACTIONS.contains(action)) {
            return permissionApi.hasAnyPermissions(actorId, PreparationItemApplicationService.PERMISSION_FILL)
                    && "DRAFT".equals(context.preparation().getStatusCode())
                    && "REQUIRED".equals(context.item().getApplicabilityCode())
                    && Objects.equals(context.item().getAssigneeUserId(), actorId);
        }
        return permissionApi.hasAnyPermissions(actorId, PreparationQueryService.PERMISSION_QUERY,
                PreparationInitializationService.PERMISSION_MANAGE,
                PreparationItemApplicationService.PERMISSION_FILL);
    }

    private Context locate(Long tenantId, String objectId, boolean lock) {
        Long itemId;
        try { itemId = Long.valueOf(objectId); } catch (RuntimeException failure) { return null; }
        if (itemId <= 0) return null;
        PreparationItemDO requested = itemMapper.selectByObjectId(new PreparationItemObjectQuery(tenantId, itemId));
        if (requested == null) return null;
        Long evidenceObjectItemId = requested.getSourceItemId() == null ? requested.getId() : requested.getSourceItemId();
        PreparationItemDO current = itemMapper.selectCurrentByEvidenceObjectId(
                new PreparationItemLineageQuery(tenantId, evidenceObjectItemId));
        PreparationItemDO item = current == null ? requested : current;
        Long currentItemId = item.getId();
        PreparationDO preparation = lock
                ? preparationMapper.selectForUpdate(new PreparationRowQuery(tenantId, item.getPreparationId()))
                : preparationMapper.selectById(new PreparationRowQuery(tenantId, item.getPreparationId()));
        if (preparation == null) return null;
        if (lock) {
            item = itemMapper.selectForUpdate(new PreparationItemRowQuery(
                    tenantId, preparation.getId(), currentItemId));
        }
        return item == null ? null : new Context(preparation, item);
    }

    private boolean inScope(ProjectScopeResult scope, Long projectId) {
        return scope != null && scope.treeVersion() != null && scope.treeVersion() >= 0
                && scope.fullProjectIds() != null && scope.fullProjectIds().contains(projectId);
    }

    private FileBusinessObjectPolicyFact policy(boolean allowed, Long scopeVersion, Context context) {
        String mutability = "DRAFT".equals(context.preparation().getStatusCode()) ? "MUTABLE" : "IMMUTABLE";
        return new FileBusinessObjectPolicyFact(allowed, scopeVersion, mutability, "MULTIPLE",
                Set.of(PURPOSE_CODE), MEDIA_TYPES, MAX_SIZE_BYTES, "INTERNAL");
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, null, null, Set.of(), Set.of(), null, null);
    }

    private record Context(PreparationDO preparation, PreparationItemDO item) {}
}
