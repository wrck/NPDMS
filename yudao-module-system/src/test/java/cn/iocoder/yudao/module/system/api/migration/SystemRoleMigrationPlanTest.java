package cn.iocoder.yudao.module.system.api.migration;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SystemRoleMigrationPlanTest {
    private static final LocalDateTime HISTORICAL = LocalDateTime.of(2017, 2, 7, 17, 47, 50);
    private SystemRoleMigrationApi.Definition definition(String code, String name) {
        return new SystemRoleMigrationApi.Definition(code, name, 100, null, null, HISTORICAL, null, HISTORICAL);
    }
    private SystemRoleMigrationApi.Entry entry(String key, SystemRoleMigrationApi.Definition value, Long id,
                                               SystemRoleMigrationApi.Definition last, boolean reference) {
        return new SystemRoleMigrationApi.Entry("T_ROLE", key, value, id, last, reference);
    }
    private SystemRoleMigrationApi.Request request(boolean adopt, SystemRoleMigrationApi.Entry... entries) {
        return new SystemRoleMigrationApi.Request(1L, "integration:1:12", List.of(entries), adopt);
    }
    private SystemRoleMigrationApi.Role role(long id, SystemRoleMigrationApi.Definition value) {
        return new SystemRoleMigrationApi.Role(id, value, 0, 2, 1, Set.of(27L));
    }
    @Test void newBusinessRoleDoesNotInheritTheNormalCreateApiAllDataDefault() {
        var result = SystemRoleMigrationApiImpl.plan(request(false, entry("2", definition("user", "普通用户"), null, null, false)), List.of()).getFirst();
        assertEquals("CREATED", result.action());
        assertEquals(2, result.after().type()); assertEquals(5, result.after().dataScope());
        assertTrue(result.after().dataScopeDeptIds().isEmpty());
        assertEquals(HISTORICAL, result.after().definition().createTime());
        assertNull(result.after().definition().creator());
    }
    @Test void superAdminCanOnlyBeExplicitlyAssociatedAndNeverRewritten() {
        var existing = new SystemRoleMigrationApi.Role(1L, definition("super_admin", "超级管理员"), 0, 1, 1, Set.of());
        var requested = definition("super_admin", "旧系统管理员");
        var reference = entry("1", requested, null, null, true);
        var result = SystemRoleMigrationApiImpl.plan(request(true, reference), List.of(existing)).getFirst();
        assertEquals("ADOPTED", result.action()); assertEquals(existing, result.after());
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(true, entry("1", requested, null, null, false)), List.of(existing)).getFirst().action());
    }
    @Test void officeManagerCodeAloneIsInsufficientToCreateProjectOrMenuPrivileges() {
        var entry = entry("9", definition("areaLeader", "办事处主任"), null, null, false);
        var result = SystemRoleMigrationApiImpl.plan(request(false, entry), List.of()).getFirst();
        assertEquals(5, result.after().dataScope()); assertEquals(0, result.after().status());
    }
    @Test void referencesToATRoleCreatedInSameBatchWorkRegardlessOfSourceOrder() {
        var primary = entry("2", definition("user", "普通用户"), null, null, false);
        var reference = new SystemRoleMigrationApi.Entry("FND_ROLE", "3", definition("user", "旧普通用户"), null, null, true);
        var results = SystemRoleMigrationApiImpl.plan(request(false, reference, primary), List.of());
        assertEquals("ADOPTED", results.getFirst().action()); assertEquals("CREATED", results.getLast().action());
        assertEquals(results.getLast().after(), results.getFirst().after());
    }
    @Test void anEstablishedSourceCannotRebindOrRecreateADeletedRole() {
        var value = definition("user", "普通用户");
        var bound = entry("2", value, 17L, value, false);
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(true, bound), List.of()).getFirst().action());
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(true, bound), List.of(role(18L, value))).getFirst().action());
    }
    @Test void manualMetadataChangesAreNotOverwrittenByReplay() {
        var previous = definition("user", "普通用户");
        var current = definition("user", "人工维护名称");
        var result = SystemRoleMigrationApiImpl.plan(request(false, entry("2", previous, 17L, previous, false)), List.of(role(17L, current))).getFirst();
        assertEquals("CONFLICT", result.action()); assertEquals(current, result.before().definition());
    }
    @Test void ownedMetadataUpdatePreservesCurrentPermissionAndAuditOperatorsAreNotInvented() {
        var previous = definition("user", "普通用户");
        var incoming = definition("user", "新普通用户");
        var existing = role(17L, previous);
        var result = SystemRoleMigrationApiImpl.plan(request(false, entry("2", incoming, 17L, previous, false)), List.of(existing)).getFirst();
        assertEquals("UPDATED", result.action()); assertEquals(existing.dataScope(), result.after().dataScope());
        assertEquals(existing.dataScopeDeptIds(), result.after().dataScopeDeptIds());
        assertNull(result.after().definition().updater());
    }
    @Test void inactiveCurrentRoleIsNotReactivatedByReference() {
        var value = definition("SERVICE_MANAGER", "服务经理");
        var inactive = new SystemRoleMigrationApi.Role(17L, value, 1, 2, 5, Set.of());
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(true, entry("11", value, 17L, null, true)), List.of(inactive)).getFirst().action());
    }
    @Test void unapprovedNameAdoptionAndTruncationAreBlocked() {
        var existing = role(17L, definition("user", "普通用户"));
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(false, entry("2", existing.definition(), null, null, false)), List.of(existing)).getFirst().action());
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(true, entry("2", definition("user", "来源新名称"), null, null, false)), List.of(existing)).getFirst().action());
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(true, entry("3", definition("viewer", "普通用户"), null, null, false)), List.of(existing)).getFirst().action());
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(false, entry("2", definition("viewer", "长".repeat(31)), null, null, false)), List.of()).getFirst().action());
    }
    @Test void missingHistoricalTimeIsNotFilledWithExecutionTime() {
        var value = new SystemRoleMigrationApi.Definition("user", "普通用户", 100, null, null, HISTORICAL, null, null);
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(false, entry("2", value, null, null, false)), List.of()).getFirst().action());
    }
    @Test void duplicateSourceIdentityAndConflictingPrimaryDefinitionsAreRejected() {
        var value = definition("user", "普通用户");
        var first = entry("2", value, null, null, false);
        assertThrows(IllegalArgumentException.class, () -> SystemRoleMigrationApiImpl.plan(request(false, first, first), List.of()));
        var second = new SystemRoleMigrationApi.Entry("FND_ROLE", "3", value, null, null, false);
        assertThrows(IllegalArgumentException.class, () -> SystemRoleMigrationApiImpl.plan(request(false, first, second), List.of()));
    }
    @Test void caseVariantsCannotCreateDuplicateCodesOrAdoptAnUnapprovedPlatformCode() {
        var existing = role(17L, definition("USER", "普通用户"));
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(true, entry("2", definition("user", "来源普通用户"), null, null, false)), List.of(existing)).getFirst().action());
        var platform = role(18L, definition("service_manager", "服务经理"));
        assertEquals("CONFLICT", SystemRoleMigrationApiImpl.plan(request(true, entry("11", definition("SERVICE_MANAGER", "服务经理"), null, null, true)), List.of(platform)).getFirst().action());
    }
}
