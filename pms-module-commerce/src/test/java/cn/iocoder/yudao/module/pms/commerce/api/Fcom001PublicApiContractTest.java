package cn.iocoder.yudao.module.pms.commerce.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Fcom001PublicApiContractTest {

    @Test
    void exposesAuthorityWriteBoundaryWithoutThirdPartyAdapter() throws Exception {
        Class<?> api = type("cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityWriteApi");
        Class<?> command = type("cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityWriteCommand");
        Class<?> result = type("cn.iocoder.yudao.module.pms.commerce.api.authority.dto.AuthorityWriteResult");

        Method apply = api.getDeclaredMethod("apply", command);

        assertEquals(result, apply.getReturnType());
        assertTrue(command.isRecord());
        assertTrue(result.isRecord());
    }

    @Test
    void locksCurrentScopeVersionsWithTheApprovedIdentity() throws Exception {
        Class<?> api = type("cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeAcceptanceLockApi");
        Class<?> command = type("cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeAcceptanceLockCommand");
        Class<?> fact = type("cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeVersionFact");

        Method lock = api.getDeclaredMethod("lockCurrentByProject", command);

        assertEquals(List.class, lock.getReturnType());
        assertRecordComponents(command, "tenantId", "projectId", "projectStageSnapshotId", "operationId");
        assertRecordComponents(fact, "deliveryScopeId", "allocationVersion");
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
