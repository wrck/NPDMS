package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormInstanceRowQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamicFormFilePolicyProvider implements FileBusinessObjectPolicyProvider {

    static final String OWNER_CONTEXT = "PLATFORM";
    static final String OBJECT_TYPE = "DYNAMIC_FORM_INSTANCE";
    static final String INSTANCE_OBJECT_TYPE = "MANUAL_DYNAMIC_FORM";
    static final String CATEGORY_CODE = "DYNAMIC_FORM_ATTACHMENT";
    private static final long MAX_SIZE_BYTES = 52_428_800L;
    private static final Set<String> CATEGORIES = Set.of(CATEGORY_CODE);
    private static final Set<String> MEDIA_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "text/plain", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    private static final Set<String> WRITE_ACTIONS = Set.of(FileActionCodes.UPLOAD, FileActionCodes.REFERENCE,
            FileActionCodes.REPLACE, FileActionCodes.DETACH);
    private static final Set<String> READ_ACTIONS = Set.of(
            FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);

    private final PlatformDynamicFormInstanceMapper instanceMapper;
    private final DynamicFormTemplateRevisionMapper revisionMapper;
    private final DynamicFormSchemaService schemaService;
    private final PermissionApi permissionApi;

    @Override
    public String ownerContext() {
        return OWNER_CONTEXT;
    }

    @Override
    public String objectType() {
        return OBJECT_TYPE;
    }

    @Override
    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        String fieldKey = parsePurpose(query.ownerContext(), query.objectType(), query.purposeCode());
        if (fieldKey == null || !validReferenceKey(query.referenceKey())) return denied();
        return inspect(query.tenantId(), query.actorUserId(), query.objectId(), fieldKey, query.requiredAction(), false,
                null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        String fieldKey = parsePurpose(query.ownerContext(), query.objectType(), query.purposeCode());
        if (fieldKey == null || !validReferenceKey(query.referenceKey())) return denied();
        return inspect(query.tenantId(), query.actorUserId(), query.objectId(), fieldKey, query.requiredAction(), true,
                query.expectedScopeVersion());
    }

    @Override
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        String fieldKey = parsePurpose(query.key());
        if (fieldKey == null) return denied();
        return inspect(query.tenantId(), query.actorUserId(), query.key().objectId(), fieldKey,
                query.requiredAction(), false, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        String fieldKey = parsePurpose(query.key());
        if (fieldKey == null) return denied();
        return inspect(query.tenantId(), query.actorUserId(), query.key().objectId(), fieldKey,
                query.requiredAction(), true, query.expectedScopeVersion());
    }

    private FileBusinessObjectPolicyFact inspect(Long tenantId, Long actorUserId, String objectId, String fieldKey,
                                                 String action, boolean lock, Long expectedScopeVersion) {
        Long instanceId = parseInstanceId(objectId);
        if (instanceId == null) return denied();
        try {
            PlatformDynamicFormInstanceDO instance = lock
                    ? instanceMapper.selectForUpdate(new DynamicFormInstanceLockQuery(tenantId, instanceId))
                    : instanceMapper.selectByRow(new DynamicFormInstanceRowQuery(tenantId, instanceId));
            if (!validInstanceBinding(tenantId, instanceId, instance)
                    || (lock && !Objects.equals(instance.getTemplateRevisionId(), expectedScopeVersion))) {
                return denied();
            }
            if (!authorized(actorUserId, action, instance)) return policy(false, instance.getTemplateRevisionId());
            DynamicFormTemplateRevisionDO revision = revisionMapper.selectByRow(
                    new DynamicFormRevisionRowQuery(tenantId, instance.getTemplateRevisionId()));
            if (!validFrozenRevision(instance, revision)) return denied();
            DynamicFormSchemaService.SchemaFields fields = schemaService.parseAndValidate(
                    revision.getFormConfJson(), revision.getFormRulesJson(), revision.getEngineCode(),
                    revision.getDesignerVersion(), revision.getRendererVersion());
            return policy(fields.isFileField(fieldKey), instance.getTemplateRevisionId());
        } catch (RuntimeException unavailable) {
            return denied();
        }
    }

    private boolean authorized(Long actorUserId, String action, PlatformDynamicFormInstanceDO instance) {
        if (READ_ACTIONS.contains(action)) {
            return permissionApi.hasAnyPermissions(actorUserId, DynamicFormActionProjection.INSTANCE_QUERY);
        }
        if (WRITE_ACTIONS.contains(action)) {
            return Objects.equals(instance.getCreatedBy(), actorUserId)
                    && permissionApi.hasAnyPermissions(actorUserId, DynamicFormActionProjection.INSTANCE_UPDATE);
        }
        return false;
    }

    private boolean validFrozenRevision(PlatformDynamicFormInstanceDO instance, DynamicFormTemplateRevisionDO revision) {
        return revision != null && "PUBLISHED".equals(revision.getStatusCode())
                && Objects.equals(revision.getTemplateId(), instance.getTemplateId())
                && Objects.equals(revision.getId(), instance.getTemplateRevisionId())
                && Objects.equals(revision.getRevisionNo(), instance.getTemplateRevisionNo())
                && Objects.equals(revision.getEngineCode(), instance.getEngineCode())
                && Objects.equals(revision.getDesignerVersion(), instance.getDesignerVersion())
                && Objects.equals(revision.getRendererVersion(), instance.getRendererVersion());
    }

    private boolean validInstanceBinding(Long tenantId, Long instanceId, PlatformDynamicFormInstanceDO instance) {
        return instance != null && Objects.equals(instance.getTenantId(), tenantId)
                && Objects.equals(instance.getId(), instanceId) && OWNER_CONTEXT.equals(instance.getOwnerContext())
                && INSTANCE_OBJECT_TYPE.equals(instance.getObjectType())
                && String.valueOf(instanceId).equals(instance.getObjectId())
                && instance.getTemplateRevisionId() != null;
    }

    private String parsePurpose(FileReferenceSetKey key) {
        if (key == null) return null;
        return parsePurpose(key.ownerContext(), key.objectType(), key.purposeCode());
    }

    private String parsePurpose(String ownerContext, String objectType, String purposeCode) {
        if (!OWNER_CONTEXT.equals(ownerContext) || !OBJECT_TYPE.equals(objectType) || purposeCode == null
                || !purposeCode.startsWith(DynamicFormSchemaService.FILE_PURPOSE_PREFIX)) {
            return null;
        }
        String fieldKey = purposeCode.substring(DynamicFormSchemaService.FILE_PURPOSE_PREFIX.length());
        if (fieldKey.isBlank() || fieldKey.contains("/")
                || fieldKey.length() > DynamicFormSchemaService.MAX_FILE_FIELD_KEY_LENGTH) {
            return null;
        }
        return fieldKey;
    }

    private boolean validReferenceKey(String referenceKey) {
        try {
            return UUID.fromString(referenceKey).toString().equalsIgnoreCase(referenceKey);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private Long parseInstanceId(String objectId) {
        try {
            long parsed = Long.parseLong(objectId);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private FileBusinessObjectPolicyFact policy(boolean allowed, Long scopeVersion) {
        return new FileBusinessObjectPolicyFact(allowed, scopeVersion, "MUTABLE", "MULTIPLE",
                CATEGORIES, MEDIA_TYPES, MAX_SIZE_BYTES, "INTERNAL");
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, null, null, Set.of(), Set.of(), null, null);
    }
}
