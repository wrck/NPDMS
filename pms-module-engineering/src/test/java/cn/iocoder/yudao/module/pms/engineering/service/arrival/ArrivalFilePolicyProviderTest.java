package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArrivalFilePolicyProviderTest {

    private static final Long TENANT = 1L;
    private static final Long ACTOR = 100L;

    @Mock private ArrivalMapper arrivalMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @InjectMocks private ArrivalFilePolicyProvider provider;

    @Test
    void pendingSignRecordAllowsEveryMutatingActionWithSingleSignSlot() {
        stubLocated(0);
        stubUpdatePermission();
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        var fact = provider.inspect(query(FileActionCodes.UPLOAD));

        assertTrue(fact.allowed());
        assertEquals("SINGLE", fact.cardinality());
        assertEquals("MUTABLE", fact.referenceMutability());
        assertEquals(Set.of(ArrivalFilePolicyProvider.PURPOSE_CODE), fact.allowedCategoryCodes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPLOAD", "REPLACE", "REFERENCE", "DETACH"})
    void editableRecordAllowsEveryApprovedMutatingAction(String action) {
        stubLocated(2);
        stubUpdatePermission();
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        assertTrue(provider.inspect(query(action)).allowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"READ", "DOWNLOAD", "PREVIEW"})
    void projectViewerCanUseEveryApprovedReadAction(String action) {
        stubLocated(1);
        when(permissionApi.hasAnyPermissions(ACTOR, "pms:imp-arrival:query",
                "pms:imp-arrival:update")).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        var fact = provider.inspect(query(action));

        assertTrue(fact.allowed());
        assertEquals("IMMUTABLE", fact.referenceMutability());
    }

    @Test
    void signedRecordFreezesTheSignDocumentEvidence() {
        stubLocated(1);
        stubUpdatePermission();
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        assertFalse(provider.inspect(query(FileActionCodes.UPLOAD)).allowed());
    }

    @Test
    void otherPurposesAndUnknownActionsAreDenied() {
        stubLocated(0);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        assertFalse(provider.inspect(query(FileActionCodes.UPLOAD, "OTHER_PURPOSE")).allowed());
        assertFalse(provider.inspect(query(FileActionCodes.ARCHIVE)).allowed());
    }

    @Test
    void foreignTenantRecordIsDenied() {
        ArrivalDO arrival = arrival(0);
        arrival.setTenantId(999L);
        when(arrivalMapper.selectById(7L)).thenReturn(arrival);

        assertFalse(provider.inspect(query(FileActionCodes.UPLOAD)).allowed());
    }

    @Test
    void outOfScopeProjectIsDenied() {
        stubLocated(0);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope(88L));

        assertFalse(provider.inspect(query(FileActionCodes.UPLOAD)).allowed());
    }

    @Test
    void lockAndRevalidateReturnsTheObservedScopeVersion() {
        ArrivalDO arrival = arrival(0);
        when(arrivalMapper.selectById(7L)).thenReturn(arrival);
        stubUpdatePermission();
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());

        when(arrivalMapper.selectDeliveryOwnerForUpdate(any())).thenReturn(arrival);
        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                TENANT, ACTOR, ArrivalFilePolicyProvider.OWNER_CONTEXT,
                ArrivalFilePolicyProvider.OBJECT_TYPE, "7", ArrivalFilePolicyProvider.PURPOSE_CODE,
                ArrivalFilePolicyProvider.REFERENCE_KEY, FileActionCodes.REFERENCE, 9L));

        assertTrue(fact.allowed());
        assertEquals(9L, fact.scopeVersion());
    }

    private void stubLocated(int status) {
        when(arrivalMapper.selectById(7L)).thenReturn(arrival(status));
    }

    private void stubUpdatePermission() {
        when(permissionApi.hasAnyPermissions(ACTOR, "pms:imp-arrival:update")).thenReturn(true);
    }

    private ArrivalDO arrival(int status) {
        ArrivalDO arrival = new ArrivalDO();
        arrival.setId(7L);
        arrival.setTenantId(TENANT);
        arrival.setProjectId(66L);
        arrival.setStatus(status);
        return arrival;
    }

    private FileBusinessObjectPolicyQuery query(String action) {
        return query(action, ArrivalFilePolicyProvider.PURPOSE_CODE);
    }

    private FileBusinessObjectPolicyQuery query(String action, String purpose) {
        return new FileBusinessObjectPolicyQuery(TENANT, ACTOR, ArrivalFilePolicyProvider.OWNER_CONTEXT,
                ArrivalFilePolicyProvider.OBJECT_TYPE, "7", purpose,
                ArrivalFilePolicyProvider.REFERENCE_KEY, action);
    }

    private ProjectScopeResult scope() {
        return scope(66L);
    }

    private ProjectScopeResult scope(Long projectId) {
        return new ProjectScopeResult(projectId, 9L, Set.of(projectId), Set.of());
    }
    @Test void signatureRaceRechecksLockedRootBeforeFileMutation(){when(arrivalMapper.selectById(7L)).thenReturn(arrival(0));when(arrivalMapper.selectDeliveryOwnerForUpdate(any())).thenReturn(arrival(1));stubUpdatePermission();when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());assertFalse(provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(TENANT,ACTOR,"IMP","ARRIVAL","7",ArrivalFilePolicyProvider.PURPOSE_CODE,ArrivalFilePolicyProvider.REFERENCE_KEY,FileActionCodes.REFERENCE,9L)).allowed());}

}
