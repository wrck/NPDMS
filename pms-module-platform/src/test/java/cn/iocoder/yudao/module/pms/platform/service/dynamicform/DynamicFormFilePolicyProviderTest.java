package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicFormFilePolicyProviderTest {

    private static final String SLOT = "2fce3d44-109d-47be-b15a-5ea09fda1a0f";
    private static final String PURPOSE = "FORM_FIELD_ATTACHMENT/drawings";

    @Mock PlatformDynamicFormInstanceMapper instanceMapper;
    @Mock DynamicFormTemplateRevisionMapper revisionMapper;
    @Mock PermissionApi permissionApi;

    private DynamicFormFilePolicyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DynamicFormFilePolicyProvider(instanceMapper, revisionMapper,
                new DynamicFormSchemaService(), permissionApi);
    }

    @Test
    void creatorWithUpdatePermissionCanMutateControlledFileSlots() {
        stubRows(false);
        when(permissionApi.hasAnyPermissions(9L, DynamicFormActionProjection.INSTANCE_UPDATE)).thenReturn(true);

        var fact = provider.inspect(query(FileActionCodes.REPLACE, PURPOSE, SLOT));

        assertTrue(fact.allowed());
        assertEquals(21L, fact.scopeVersion());
        assertEquals("MUTABLE", fact.referenceMutability());
        assertEquals("MULTIPLE", fact.cardinality());
        assertEquals(Set.of("DYNAMIC_FORM_ATTACHMENT"), fact.allowedCategoryCodes());
        assertEquals(52_428_800L, fact.maxSizeBytes());
    }

    @Test
    void queryPermissionAllowsReadButNeverMutationForAnotherUser() {
        stubRows(false);
        when(permissionApi.hasAnyPermissions(8L, DynamicFormActionProjection.INSTANCE_QUERY)).thenReturn(true);

        assertTrue(provider.inspect(query(8L, FileActionCodes.PREVIEW, PURPOSE, SLOT)).allowed());
        assertFalse(provider.inspect(query(8L, FileActionCodes.DETACH, PURPOSE, SLOT)).allowed());
        assertFalse(provider.inspect(query(8L, FileActionCodes.ARCHIVE, PURPOSE, SLOT)).allowed());
    }

    @Test
    void invalidNamespaceSlotAndOrdinaryFieldFailClosed() {
        assertFalse(provider.inspect(query(FileActionCodes.UPLOAD, "FORM_FIELD_ATTACHMENT/site/photo", SLOT)).allowed());
        assertFalse(provider.inspect(query(FileActionCodes.UPLOAD, PURPOSE, "not-a-uuid")).allowed());
        verifyNoInteractions(instanceMapper, revisionMapper, permissionApi);

        stubRows(false);
        when(permissionApi.hasAnyPermissions(9L, DynamicFormActionProjection.INSTANCE_UPDATE)).thenReturn(true);
        assertFalse(provider.inspect(query(FileActionCodes.UPLOAD,
                "FORM_FIELD_ATTACHMENT/ordinary", SLOT)).allowed());
    }

    @Test
    void mismatchedServerOwnedInstanceBindingFailsClosed() {
        PlatformDynamicFormInstanceDO row = instance();
        row.setObjectType("OTHER");
        when(instanceMapper.selectByRow(any())).thenReturn(row);

        assertFalse(provider.inspect(query(FileActionCodes.READ, PURPOSE, SLOT)).allowed());
        verifyNoInteractions(revisionMapper, permissionApi);
    }

    @Test
    void lockUsesFrozenRevisionAsScopeAndRejectsStaleExpectationBeforePermission() {
        when(instanceMapper.selectForUpdate(any())).thenReturn(instance());

        var stale = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "PLATFORM", "DYNAMIC_FORM_INSTANCE", "31", PURPOSE, SLOT,
                FileActionCodes.UPLOAD, 20L));

        assertFalse(stale.allowed());
        verifyNoInteractions(revisionMapper, permissionApi);

        when(permissionApi.hasAnyPermissions(9L, DynamicFormActionProjection.INSTANCE_UPDATE)).thenReturn(true);
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        var current = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "PLATFORM", "DYNAMIC_FORM_INSTANCE", "31", PURPOSE, SLOT,
                FileActionCodes.UPLOAD, 21L));

        assertTrue(current.allowed());
        verify(instanceMapper, never()).selectByRow(any());
    }

    @Test
    void referenceSetInspectionAndLockUseTheSameFieldNamespace() {
        FileReferenceSetKey key = new FileReferenceSetKey("PLATFORM", "DYNAMIC_FORM_INSTANCE", "31", PURPOSE);
        stubRows(false);
        when(permissionApi.hasAnyPermissions(9L, DynamicFormActionProjection.INSTANCE_QUERY)).thenReturn(true);

        var inspected = provider.inspectReferenceSet(new FileBusinessObjectReferenceSetQuery(
                0L, 9L, key, FileActionCodes.READ));

        assertTrue(inspected.allowed());
        assertEquals(21L, inspected.scopeVersion());

        when(instanceMapper.selectForUpdate(any())).thenReturn(instance());
        var locked = provider.lockAndRevalidateReferenceSet(new FileBusinessObjectReferenceSetRevalidationQuery(
                0L, 9L, key, FileActionCodes.READ, 21L));

        assertTrue(locked.allowed());
    }

    private void stubRows(boolean lock) {
        if (lock) {
            when(instanceMapper.selectForUpdate(any())).thenReturn(instance());
        } else {
            when(instanceMapper.selectByRow(any())).thenReturn(instance());
        }
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
    }

    private FileBusinessObjectPolicyQuery query(String action, String purpose, String referenceKey) {
        return query(9L, action, purpose, referenceKey);
    }

    private FileBusinessObjectPolicyQuery query(Long actorId, String action, String purpose, String referenceKey) {
        return new FileBusinessObjectPolicyQuery(0L, actorId, "PLATFORM", "DYNAMIC_FORM_INSTANCE",
                "31", purpose, referenceKey, action);
    }

    private PlatformDynamicFormInstanceDO instance() {
        PlatformDynamicFormInstanceDO row = new PlatformDynamicFormInstanceDO();
        row.setId(31L);
        row.setTenantId(0L);
        row.setOwnerContext("PLATFORM");
        row.setObjectType("MANUAL_DYNAMIC_FORM");
        row.setObjectId("31");
        row.setTemplateId(11L);
        row.setTemplateRevisionId(21L);
        row.setTemplateRevisionNo(2);
        row.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        row.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        row.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        row.setCreatedBy(9L);
        return row;
    }

    private DynamicFormTemplateRevisionDO revision() {
        DynamicFormTemplateRevisionDO row = new DynamicFormTemplateRevisionDO();
        row.setId(21L);
        row.setTenantId(0L);
        row.setTemplateId(11L);
        row.setRevisionNo(2);
        row.setStatusCode("PUBLISHED");
        row.setFormConfJson("{}");
        row.setFormRulesJson("[{\"type\":\"input\",\"field\":\"ordinary\"},"
                + "{\"type\":\"PmsFileArtifact\",\"field\":\"drawings\"}]");
        row.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        row.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        row.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        return row;
    }
}
