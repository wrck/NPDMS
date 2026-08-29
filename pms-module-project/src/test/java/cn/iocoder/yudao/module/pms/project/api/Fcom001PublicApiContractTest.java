package cn.iocoder.yudao.module.pms.project.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fcom001PublicApiContractTest {

    @Test
    void exposesProjectOfficeAndAcceptanceStageFacts() throws Exception {
        Class<?> officeApi = type("cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi");
        Class<?> officeQuery = type("cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFactQuery");
        Class<?> officeFact = type("cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact");
        Class<?> stageApi = type("cn.iocoder.yudao.module.pms.project.api.commerce.ProjectAcceptanceStageFactApi");
        Class<?> stageQuery = type("cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFactQuery");
        Class<?> stageFact = type("cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFact");

        assertEquals(officeFact, officeApi.getDeclaredMethod("resolve", officeQuery).getReturnType());
        assertEquals(officeFact, officeApi.getDeclaredMethod("lockAndRevalidate", officeQuery).getReturnType());
        assertEquals(stageFact, stageApi.getDeclaredMethod("lockAndRead", stageQuery).getReturnType());
        assertRecordComponents(officeQuery, "tenantId", "projectId", "expectedProjectVersion");
        assertRecordComponents(officeFact, "outcome", "projectId", "projectVersion", "projectCode",
                "officeDepartmentId", "officeDepartmentCode", "officeDepartmentName", "officeDepartmentVersion");
        assertRecordComponents(stageQuery, "tenantId", "projectId", "expectedProjectVersion", "operationId");
        assertRecordComponents(stageFact, "outcome", "projectId", "projectVersion", "currentStageCode",
                "acceptanceStageCode", "projectStageSnapshotId");
    }

    @Test
    void exposesAcceptanceGuardAndBindingCommands() throws Exception {
        Class<?> guardApi = type("cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeGuardApi");
        Class<?> guardQuery = type("cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardQuery");
        Class<?> guardResult = type("cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardResult");
        Class<?> bindingApi = type("cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeBindingApi");
        Class<?> stageCommand = type("cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceStageEntryBindingCommand");
        Class<?> effectiveCommand = type("cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.EffectiveScopeBindingCommand");
        Class<?> bindingResult = type("cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult");

        Method guard = guardApi.getDeclaredMethod("checkReduction", guardQuery);
        assertEquals(guardResult, guard.getReturnType());
        assertEquals(bindingResult, bindingApi.getDeclaredMethod("bindForStageEntry", stageCommand).getReturnType());
        assertEquals(bindingResult, bindingApi.getDeclaredMethod("bindEffectiveScope", effectiveCommand).getReturnType());
        assertRecordComponents(guardQuery, "tenantId", "projectId", "deliveryScopeId",
                "currentAllocationVersion", "proposedAllocatedQty", "operationId");
        assertRecordComponents(stageCommand, "tenantId", "projectId", "projectVersion",
                "projectStageSnapshotId", "fromStageCode", "acceptanceStageCode", "operationId");
        assertRecordComponents(effectiveCommand, "tenantId", "projectId", "projectStageSnapshotId",
                "deliveryScopeId", "scopeAllocationVersion", "operationId");
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertTrue(type.isRecord());
        assertEquals(List.of(expected), Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName()).toList());
    }
}
