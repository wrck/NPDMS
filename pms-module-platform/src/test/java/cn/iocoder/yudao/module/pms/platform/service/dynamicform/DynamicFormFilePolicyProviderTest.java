package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstancePolicyQuery;
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
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicFormFilePolicyProviderTest {

    private static final String SLOT = "2fce3d44-109d-47be-b15a-5ea09fda1a0f";
    private static final String PURPOSE = "FORM_FIELD_ATTACHMENT/drawings";

    @Mock PlatformDynamicFormInstanceMapper instanceMapper;
    @Mock DynamicFormTemplateRevisionMapper revisionMapper;
    @Mock PermissionApi permissionApi;
    @Mock DynamicFormBusinessObjectPolicyProviderRegistry businessPolicyRegistry;

    private DynamicFormFilePolicyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DynamicFormFilePolicyProvider(instanceMapper, revisionMapper,
                new DynamicFormSchemaService(), permissionApi, businessPolicyRegistry);
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
        when(instanceMapper.selectByRow(any())).thenReturn(instance());

        var stale = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "PLATFORM", "DYNAMIC_FORM_INSTANCE", "31", PURPOSE, SLOT,
                FileActionCodes.UPLOAD, 20L));

        assertFalse(stale.allowed());
        verifyNoInteractions(revisionMapper, permissionApi);

        when(permissionApi.hasAnyPermissions(9L, DynamicFormActionProjection.INSTANCE_UPDATE)).thenReturn(true);
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        when(instanceMapper.selectForUpdate(any())).thenReturn(instance());
        var current = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "PLATFORM", "DYNAMIC_FORM_INSTANCE", "31", PURPOSE, SLOT,
                FileActionCodes.UPLOAD, 21L));

        assertTrue(current.allowed());
        verify(instanceMapper).selectForUpdate(any());
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

    @Test
    void businessInstanceDelegatesFileActionToOwnerProvider() {
        PlatformDynamicFormInstanceDO row = instance();
        row.setOwnerContext("SOL");
        row.setObjectType("REQUIREMENT_ANALYSIS");
        row.setObjectId("77");
        when(instanceMapper.selectByRow(any())).thenReturn(row);
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        when(businessPolicyRegistry.inspectInstance(any())).thenReturn(new DynamicFormPolicyFact(
                DynamicFormBusinessAction.FILE_READ, true, null, 12L, "77:COMPLETED:12"));

        var fact = provider.inspect(query(9L, FileActionCodes.READ, PURPOSE, SLOT));

        assertTrue(fact.allowed());
        assertEquals(12L, fact.scopeVersion());
        assertEquals("IMMUTABLE", fact.referenceMutability());
        verifyNoInteractions(permissionApi);
    }

    @Test
    void businessInstanceDelegatesArchiveAndInvalidateToOwnerFileWrite() {
        PlatformDynamicFormInstanceDO row = businessInstance();
        when(instanceMapper.selectByRow(any())).thenReturn(row);
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        when(businessPolicyRegistry.inspectInstance(any())).thenReturn(new DynamicFormPolicyFact(
                DynamicFormBusinessAction.FILE_WRITE, true, null, 12L, "77:DRAFT:12"));

        assertTrue(provider.inspect(query(9L, FileActionCodes.ARCHIVE, PURPOSE, SLOT)).allowed());
        assertTrue(provider.inspect(query(9L, FileActionCodes.INVALIDATE, PURPOSE, SLOT)).allowed());

        ArgumentCaptor<DynamicFormInstancePolicyQuery> policies =
                ArgumentCaptor.forClass(DynamicFormInstancePolicyQuery.class);
        verify(businessPolicyRegistry, times(2)).inspectInstance(policies.capture());
        assertEquals(java.util.List.of(DynamicFormBusinessAction.FILE_WRITE,
                        DynamicFormBusinessAction.FILE_WRITE),
                policies.getAllValues().stream().map(DynamicFormInstancePolicyQuery::action).toList());
        verifyNoInteractions(permissionApi);
    }

    @Test
    void manualInstanceStillDeniesArchiveAndInvalidateWithoutOwnerDelegation() {
        when(instanceMapper.selectByRow(any())).thenReturn(instance());

        assertFalse(provider.inspect(query(9L, FileActionCodes.ARCHIVE, PURPOSE, SLOT)).allowed());
        assertFalse(provider.inspect(query(9L, FileActionCodes.INVALIDATE, PURPOSE, SLOT)).allowed());

        verifyNoInteractions(revisionMapper, businessPolicyRegistry);
    }

    @Test
    void businessLifecycleActionFailsClosedWhenOwnerRejects() {
        when(instanceMapper.selectByRow(any())).thenReturn(businessInstance());
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        when(businessPolicyRegistry.inspectInstance(any())).thenReturn(new DynamicFormPolicyFact(
                DynamicFormBusinessAction.FILE_WRITE, false, "FORBIDDEN", 12L, "77:DRAFT:12"));

        assertFalse(provider.inspect(query(9L, FileActionCodes.ARCHIVE, PURPOSE, SLOT)).allowed());
    }

    @Test
    void businessLifecycleLockRejectsStaleOwnerScopeBeforePlatformLock() {
        when(instanceMapper.selectByRow(any())).thenReturn(businessInstance());
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        when(businessPolicyRegistry.prevalidatedFilePolicy(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(businessPolicyRegistry.inspectInstance(any())).thenReturn(new DynamicFormPolicyFact(
                DynamicFormBusinessAction.FILE_WRITE, true, null, 13L, "77:DRAFT:13"));

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "PLATFORM", "DYNAMIC_FORM_INSTANCE", "31", PURPOSE, SLOT,
                FileActionCodes.INVALIDATE, 12L));

        assertFalse(fact.allowed());
        verify(businessPolicyRegistry, never()).lockAndRevalidate(any());
        verify(instanceMapper, never()).selectForUpdate(any());
    }

    @Test
    void businessFileLockConsumesProviderFactValidatedBeforePlatformLocks() {
        PlatformDynamicFormInstanceDO row = instance();
        row.setOwnerContext("SOL");
        row.setObjectType("REQUIREMENT_ANALYSIS");
        row.setObjectId("77");
        when(instanceMapper.selectByRow(any())).thenReturn(row);
        when(instanceMapper.selectForUpdate(any())).thenReturn(row);
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        when(businessPolicyRegistry.prevalidatedFilePolicy(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new DynamicFormPolicyFact(
                        DynamicFormBusinessAction.FILE_READ, true, null, 12L, "77:DRAFT:12")));

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "PLATFORM", "DYNAMIC_FORM_INSTANCE", "31", PURPOSE, SLOT,
                FileActionCodes.READ, 12L));

        assertTrue(fact.allowed());
        assertEquals(12L, fact.scopeVersion());
        verify(businessPolicyRegistry, never()).inspectInstance(any());
        verify(businessPolicyRegistry, never()).lockAndRevalidate(any());
    }

    @Test
    void businessFileLockValidatesOwnerProviderBeforeFirstPlatformLockWithoutPrecache() {
        PlatformDynamicFormInstanceDO row = instance();
        row.setOwnerContext("SOL");
        row.setObjectType("REQUIREMENT_ANALYSIS");
        row.setObjectId("77");
        DynamicFormPolicyFact inspected = new DynamicFormPolicyFact(
                DynamicFormBusinessAction.FILE_READ, true, null, 12L, "77:DRAFT:12");
        when(instanceMapper.selectByRow(any())).thenReturn(row);
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        when(businessPolicyRegistry.prevalidatedFilePolicy(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(businessPolicyRegistry.inspectInstance(any())).thenReturn(inspected);
        when(businessPolicyRegistry.lockAndRevalidate(any())).thenReturn(inspected);
        when(instanceMapper.selectForUpdate(any())).thenReturn(row);

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "PLATFORM", "DYNAMIC_FORM_INSTANCE", "31", PURPOSE, SLOT,
                FileActionCodes.READ, 12L));

        assertTrue(fact.allowed());
        InOrder order = inOrder(instanceMapper, businessPolicyRegistry);
        order.verify(instanceMapper).selectByRow(any());
        order.verify(businessPolicyRegistry).prevalidatedFilePolicy(any(), any(), any(), any(), any(), any());
        order.verify(businessPolicyRegistry).inspectInstance(any());
        order.verify(businessPolicyRegistry).lockAndRevalidate(any());
        order.verify(instanceMapper).selectForUpdate(any());
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
        row.setVersion(4);
        return row;
    }

    private PlatformDynamicFormInstanceDO businessInstance() {
        PlatformDynamicFormInstanceDO row = instance();
        row.setOwnerContext("SOL");
        row.setObjectType("REQUIREMENT_ANALYSIS");
        row.setObjectId("77");
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
