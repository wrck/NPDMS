package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
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

/**
 * 到货签收单附件的文件操作策略：签收单在待签收/异常阶段可换版，签收后证据定格。
 * 归集语义见 {@link ArrivalDocumentSources}（保存后签收单自动归入交付清单 D_ARRIVAL）。
 */
@Component
@RequiredArgsConstructor
public class ArrivalFilePolicyProvider implements FileBusinessObjectPolicyProvider {

    public static final String OWNER_CONTEXT = "IMP";
    public static final String OBJECT_TYPE = "ARRIVAL";
    public static final String PURPOSE_CODE = "ARRIVAL_SIGN_DOCUMENT";
    public static final String REFERENCE_KEY = "arrival-sign-document";
    private static final long MAX_SIZE_BYTES = 52_428_800L;
    private static final Set<String> MEDIA_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "text/plain", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final Set<String> MUTATING_ACTIONS = Set.of(
            FileActionCodes.UPLOAD, FileActionCodes.REPLACE,
            FileActionCodes.REFERENCE, FileActionCodes.DETACH);
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            FileActionCodes.UPLOAD, FileActionCodes.REPLACE, FileActionCodes.REFERENCE,
            FileActionCodes.DETACH, FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);
    private static final String PERMISSION_UPDATE = "pms:imp-arrival:update";
    private static final String PERMISSION_QUERY = "pms:imp-arrival:query";

    private final ArrivalMapper arrivalMapper;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return lockAndRevalidate(query.tenantId(), query.actorUserId(), query.objectId(),
                query.purposeCode(), query.requiredAction(), query.expectedScopeVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        return lockAndRevalidate(query.tenantId(), query.actorUserId(), query.key().objectId(),
                query.key().purposeCode(), query.requiredAction(), query.expectedScopeVersion());
    }

    private FileBusinessObjectPolicyFact inspect(Long tenantId, Long actorUserId, String objectId,
                                                 String purposeCode, String requiredAction) {
        ArrivalDO arrival = locate(tenantId, objectId);
        if (arrival == null || !PURPOSE_CODE.equals(purposeCode)) return denied();
        ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorUserId, arrival.getProjectId(), ProjectScopeApi.ACTION_VIEW));
        if (!inScope(scope, arrival.getProjectId())) return denied();
        return policy(allowed(actorUserId, requiredAction, arrival), scope.treeVersion(), arrival);
    }

    private FileBusinessObjectPolicyFact lockAndRevalidate(Long tenantId, Long actorUserId,
                                                           String objectId, String purposeCode,
                                                           String requiredAction, Long expectedScopeVersion) {
        ArrivalDO located = locate(tenantId, objectId);
        if (located == null || !PURPOSE_CODE.equals(purposeCode)) return denied();
        ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                tenantId, actorUserId, located.getProjectId(),
                ProjectScopeApi.ACTION_VIEW, expectedScopeVersion));
        if (!inScope(scope, located.getProjectId())) return denied();
        ArrivalDO locked = locate(tenantId, objectId);
        if (locked == null || !Objects.equals(locked.getProjectId(), located.getProjectId())) return denied();
        return policy(allowed(actorUserId, requiredAction, locked), scope.treeVersion(), locked);
    }

    private boolean allowed(Long actorId, String action, ArrivalDO arrival) {
        if (!SUPPORTED_ACTIONS.contains(action)) return false;
        if (MUTATING_ACTIONS.contains(action)) {
            return permissionApi.hasAnyPermissions(actorId, PERMISSION_UPDATE) && mutable(arrival);
        }
        return permissionApi.hasAnyPermissions(actorId, PERMISSION_QUERY, PERMISSION_UPDATE);
    }

    private boolean mutable(ArrivalDO arrival) {
        return arrival.getStatus() != null && arrival.getStatus() != 1;
    }

    private ArrivalDO locate(Long tenantId, String objectId) {
        Long arrivalId;
        try {
            arrivalId = Long.valueOf(objectId);
        } catch (RuntimeException failure) {
            return null;
        }
        if (arrivalId <= 0) return null;
        ArrivalDO arrival = arrivalMapper.selectById(arrivalId);
        return arrival == null || !Objects.equals(tenantId, arrival.getTenantId()) ? null : arrival;
    }

    private boolean inScope(ProjectScopeResult scope, Long projectId) {
        return scope != null && scope.treeVersion() != null && scope.treeVersion() >= 0
                && scope.fullProjectIds() != null && scope.fullProjectIds().contains(projectId);
    }

    private FileBusinessObjectPolicyFact policy(boolean allowed, Long scopeVersion, ArrivalDO arrival) {
        String mutability = mutable(arrival) ? "MUTABLE" : "IMMUTABLE";
        return new FileBusinessObjectPolicyFact(allowed, scopeVersion, mutability, "SINGLE",
                Set.of(PURPOSE_CODE), MEDIA_TYPES, MAX_SIZE_BYTES, "INTERNAL");
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, null, null, Set.of(), Set.of(), null, null);
    }
}
