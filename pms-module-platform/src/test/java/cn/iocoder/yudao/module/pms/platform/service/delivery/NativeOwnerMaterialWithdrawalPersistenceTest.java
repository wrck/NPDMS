package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.NativeOwnerAction;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.NativeOwnerActionRequest;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Real public Spring/MyBatis transactions; the native authorization SPI is a controlled port. */
class NativeOwnerMaterialWithdrawalPersistenceTest extends DeliveryMaterialWithdrawalPersistenceTest {
    static final AtomicBoolean nativeWithdraw = new AtomicBoolean(), nativeTerminate = new AtomicBoolean();
    static final AtomicBoolean returnInvalidOwnerVersion = new AtomicBoolean();
    static List<DeliveryMaterialUploadPolicyValidator> nativeAdapters;

    @BeforeEach void useNativeOwnerContract() {
        context.close();
        jdbc.execute("ALTER TABLE native_owner ADD COLUMN status INTEGER DEFAULT 0");
        jdbc.execute("ALTER TABLE native_owner ADD COLUMN scope_visible BOOLEAN DEFAULT TRUE");
        jdbc.execute("ALTER TABLE native_owner ADD COLUMN project_active BOOLEAN DEFAULT TRUE");
        nativeWithdraw.set(true); nativeTerminate.set(true); returnInvalidOwnerVersion.set(false);
        context = new AnnotationConfigApplicationContext(NativeConfig.class);
        api = context.getBean(PlatformDeliveryMaterialApi.class);
    }

    NativeOwnerActionRequest request(NativeOwnerAction action) {
        return new NativeOwnerActionRequest("NATIVE", "note", 101L, 3L, action);
    }

    TransactionTemplate tx() {
        return new TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    }

    PlatformDeliveryMaterialApi.MaterialWithdrawal run(NativeOwnerActionRequest action, long materialVersion, String key, String reason) {
        return tx().execute(status -> api.withdrawMaterialForOwnerAction(action, 901L, materialVersion, key, reason));
    }

    void submitted(int status) { jdbc.update("UPDATE native_owner SET status=? WHERE id=101", status); }

    void nativeCas(int nextStatus) {
        int observedState = jdbc.queryForObject("SELECT status FROM native_owner WHERE id=101", Integer.class);
        assertEquals(1, jdbc.update("UPDATE native_owner SET status=?,version=version+1 WHERE id=101 AND tenant_id=7 AND version=3 AND status=?", nextStatus, observedState));
    }

    void noWrites() {
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=901", String.class));
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM plt_delivery_material WHERE id=901", Long.class));
        assertTrue(counts().values().stream().allMatch(value -> value == 0)); anchors();
    }

    @Test void submittedOwnerUsesNativeWithdrawalWithoutGenericUploadAccess() {
        submitted(1);
        assertThrows(BusinessContractException.class, () -> api.withdrawMaterial(901L, 0L, "generic", "NativeOwnerAction=WITHDRAW"));
        noWrites(); permission.set(false);
        var result = tx().execute(status -> {
            var response = api.withdrawMaterialForOwnerAction(request(NativeOwnerAction.WITHDRAW), 901L, 0L, "native", "owner withdrawal");
            nativeCas(5); return response;
        });
        assertEquals(1L, result.version()); assertEquals("WITHDRAWN", result.status());
        assertEquals(5, jdbc.queryForObject("SELECT status FROM native_owner WHERE id=101", Integer.class));
        assertEquals(4L, jdbc.queryForObject("SELECT version FROM native_owner WHERE id=101", Long.class));
        assertEquals("DELIVERY_MATERIAL_OWNER_WITHDRAW", jdbc.queryForObject("SELECT scope_code FROM plt_idempotency_record", String.class));
        assertEquals(1L, counts().get("plt_operation_audit")); assertEquals(1L, counts().get("plt_outbox_event")); anchors();
    }

    @Test void approvedOwnerTerminationUsesItsNativeActionContract() {
        submitted(2); nativeWithdraw.set(false); permission.set(false);
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "wrong-action", "reason"));
        noWrites();
        tx().executeWithoutResult(status -> {
            api.withdrawMaterialForOwnerAction(request(NativeOwnerAction.TERMINATE), 901L, 0L, "terminate", "owner termination");
            nativeCas(6);
        });
        assertEquals(6, jdbc.queryForObject("SELECT status FROM native_owner WHERE id=101", Integer.class));
        assertEquals("DELIVERY_MATERIAL_OWNER_TERMINATE", jdbc.queryForObject("SELECT scope_code FROM plt_idempotency_record", String.class));
        assertTrue(jdbc.queryForObject("SELECT detail_snapshot FROM plt_operation_audit", String.class).contains("TERMINATE")); anchors();
    }

    @ParameterizedTest
    @CsvSource({"WITHDRAW,1,5", "WITHDRAW,2,5", "TERMINATE,0,6", "TERMINATE,1,6", "TERMINATE,2,6", "TERMINATE,4,6", "TERMINATE,5,6"})
    void nativeOwnerContractAcceptsAllDeclaredStates(NativeOwnerAction action, int originalState, int nextState) {
        submitted(originalState); permission.set(false);
        tx().executeWithoutResult(status -> {
            api.withdrawMaterialForOwnerAction(request(action), 901L, 0L, "state-contract", "reason"); nativeCas(nextState);
        });
        assertEquals(nextState, jdbc.queryForObject("SELECT status FROM native_owner WHERE id=101", Integer.class));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM plt_delivery_material WHERE id=901", Long.class));
        assertEquals(1L, counts().get("plt_outbox_event")); anchors();
    }

    @Test void nativeOwnerActionCannotCreateItsOwnTransaction() {
        submitted(1);
        assertThrows(IllegalTransactionStateException.class, () -> api.withdrawMaterialForOwnerAction(request(NativeOwnerAction.WITHDRAW), 901L, 0L, "standalone", "reason"));
        noWrites();
    }

    @Test void genericPermissionCannotSubstituteForNativeActionPermissionOrState() {
        submitted(1); nativeWithdraw.set(false);
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "native-denied", "reason"));
        nativeWithdraw.set(true); nativeTerminate.set(false);
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.TERMINATE), 0, "terminate-denied", "reason"));
        nativeTerminate.set(true);
        for (int state : List.of(0, 3, 4, 5, 6)) {
            submitted(state);
            assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "withdraw-state-" + state, "reason"));
            if (state == 3 || state == 6)
                assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.TERMINATE), 0, "terminate-state-" + state, "reason"));
        }
        noWrites();
    }

    @Test void absentOrDuplicateExplicitNativeAdapterCannotFallBackToGenericWrites() {
        jdbc.update("UPDATE plt_delivery_material SET entity_type='legacy' WHERE id=901");
        var legacy = new NativeOwnerActionRequest("NATIVE", "legacy", 101L, 3L, NativeOwnerAction.WITHDRAW);
        assertThrows(BusinessContractException.class, () -> run(legacy, 0, "legacy", "reason"));
        jdbc.update("UPDATE plt_delivery_material SET owner_module='UNKNOWN',entity_type='note' WHERE id=901");
        var unknown = new NativeOwnerActionRequest("UNKNOWN", "note", 101L, 3L, NativeOwnerAction.WITHDRAW);
        assertThrows(BusinessContractException.class, () -> run(unknown, 0, "unknown", "reason"));
        jdbc.update("UPDATE plt_delivery_material SET owner_module='NATIVE' WHERE id=901");
        nativeAdapters.add(nativeAdapters.getFirst()); submitted(1);
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "duplicate", "reason"));
        noWrites();
    }

    @Test void requestedOwnerMustMatchMaterialAndLockedOwnerVersion() {
        submitted(1);
        for (var wrong : List.of(
                new NativeOwnerActionRequest("OTHER", "note", 101L, 3L, NativeOwnerAction.WITHDRAW),
                new NativeOwnerActionRequest("NATIVE", "other", 101L, 3L, NativeOwnerAction.WITHDRAW),
                new NativeOwnerActionRequest("NATIVE", "note", 102L, 3L, NativeOwnerAction.WITHDRAW),
                new NativeOwnerActionRequest("NATIVE", "note", 101L, 2L, NativeOwnerAction.WITHDRAW)))
            assertThrows(BusinessContractException.class, () -> run(wrong, 0, "wrong-owner", "reason"));
        returnInvalidOwnerVersion.set(true);
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "invalid-owner-fact", "reason"));
        noWrites();
    }

    @Test void invalidNativeRequestFailsWithoutAReservation() {
        submitted(1);
        for (var invalid : List.of(
                new NativeOwnerActionRequest(null, "note", 101L, 3L, NativeOwnerAction.WITHDRAW),
                new NativeOwnerActionRequest("NATIVE", "", 101L, 3L, NativeOwnerAction.WITHDRAW),
                new NativeOwnerActionRequest("NATIVE", "note", 0L, 3L, NativeOwnerAction.WITHDRAW),
                new NativeOwnerActionRequest("NATIVE", "note", 101L, null, NativeOwnerAction.WITHDRAW),
                new NativeOwnerActionRequest("NATIVE", "note", 101L, -1L, NativeOwnerAction.WITHDRAW),
                new NativeOwnerActionRequest("NATIVE", "note", 101L, 3L, null)))
            assertThrows(BusinessContractException.class, () -> run(invalid, 0, "invalid", "reason"));
        assertThrows(BusinessContractException.class, () -> run(null, 0, "null", "reason")); noWrites();
    }

    @Test void missingPrincipalTenantAndCurrentOwnerScopeAreRevalidated() {
        submitted(1);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "principal", "reason"));
        login(8, 9); assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "tenant", "reason"));
        login(7, 9);
        jdbc.update("UPDATE native_owner SET scope_visible=FALSE WHERE id=101");
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "scope", "reason"));
        jdbc.update("UPDATE native_owner SET scope_visible=TRUE,project_active=FALSE WHERE id=101");
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "project", "reason"));
        jdbc.update("DELETE FROM native_owner WHERE id=101");
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "owner", "reason")); noWrites();
    }

    @Test void completedNativeReplayStillRequiresOriginalActionAndCurrentOwner() {
        submitted(1); var action = request(NativeOwnerAction.WITHDRAW);
        var first = run(action, 0, "replay", "reason"); assertEquals(first, run(action, 0, "replay", "reason"));
        var before = counts(); nativeWithdraw.set(false);
        assertThrows(BusinessContractException.class, () -> run(action, 0, "replay", "reason")); nativeWithdraw.set(true);
        jdbc.update("UPDATE native_owner SET scope_visible=FALSE WHERE id=101");
        assertThrows(BusinessContractException.class, () -> run(action, 0, "replay", "reason"));
        jdbc.update("UPDATE native_owner SET scope_visible=TRUE,version=4 WHERE id=101");
        assertThrows(BusinessContractException.class, () -> run(action, 0, "replay", "reason"));
        assertEquals(before, counts()); anchors();
    }

    @Test void nativeReplayInTheSameOuterTransactionDoesNotMarkRollbackOnly() {
        submitted(1); var action = request(NativeOwnerAction.WITHDRAW);
        tx().executeWithoutResult(status -> {
            var response = api.withdrawMaterialForOwnerAction(action, 901L, 0L, "outer-replay", "reason");
            assertEquals(response, api.withdrawMaterialForOwnerAction(action, 901L, 0L, "outer-replay", "reason"));
            nativeCas(5);
        });
        assertEquals(5, jdbc.queryForObject("SELECT status FROM native_owner WHERE id=101", Integer.class));
        assertEquals(1L, counts().get("plt_idempotency_record")); assertEquals(1L, counts().get("plt_operation_audit")); anchors();
    }

    @Test void genericAndDifferentNativeActionKeysCannotReplayEachOther() {
        api.withdrawMaterial(901L, 0L, "shared-key", "reason"); submitted(1);
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.WITHDRAW), 0, "shared-key", "reason"));
        assertEquals("DELIVERY_MATERIAL_WITHDRAW", jdbc.queryForObject("SELECT scope_code FROM plt_idempotency_record", String.class));
        assertEquals(1L, counts().get("plt_idempotency_record"));
        jdbc.update("DELETE FROM plt_idempotency_record"); jdbc.update("DELETE FROM plt_operation_audit"); jdbc.update("DELETE FROM plt_outbox_event");
        jdbc.update("UPDATE plt_delivery_material SET status='ACTIVE',version=0 WHERE id=901");
        run(request(NativeOwnerAction.WITHDRAW), 0, "native-key", "reason"); submitted(2);
        assertThrows(BusinessContractException.class, () -> run(request(NativeOwnerAction.TERMINATE), 0, "native-key", "reason"));
        assertEquals(1L, counts().get("plt_idempotency_record"));
        assertEquals("DELIVERY_MATERIAL_OWNER_WITHDRAW", jdbc.queryForObject("SELECT scope_code FROM plt_idempotency_record", String.class)); anchors();
    }

    @Test void changedNativeDigestOrStaleMaterialVersionCannotWrite() {
        submitted(1); var action = request(NativeOwnerAction.WITHDRAW);
        assertThrows(BusinessContractException.class, () -> run(action, 1, "stale", "reason")); noWrites();
        run(action, 0, "digest", "reason");
        assertThrows(BusinessContractException.class, () -> run(action, 0, "digest", "different reason"));
        assertEquals(1L, counts().get("plt_idempotency_record")); anchors();
    }

    @Test void nativeActionsKeepTemplateArchiveAndAllUsageProtection() {
        submitted(1);
        for (var action : NativeOwnerAction.values()) {
            for (String condition : List.of("requirement_id=1001", "archive_status='ARCHIVED'", "archive_status='PENDING_COMPENSATION'", "archive_time=CURRENT_TIMESTAMP")) {
                jdbc.update("UPDATE plt_delivery_material SET " + condition + " WHERE id=901");
                assertThrows(BusinessContractException.class, () -> run(request(action), 0, "protected", "reason"));
                jdbc.update("UPDATE plt_delivery_material SET requirement_id=NULL,archive_status='NOT_REQUIRED',archive_time=NULL WHERE id=901");
                noWrites();
            }
            for (String state : List.of("ACTIVE", "WITHDRAWN")) {
                jdbc.update("INSERT INTO plt_delivery_fulfillment(id,tenant_id,requirement_id,material_id,status) VALUES(1001,7,1002,901,?)", state);
                assertThrows(BusinessContractException.class, () -> run(request(action), 0, "used", "reason"));
                jdbc.update("DELETE FROM plt_delivery_fulfillment"); noWrites();
            }
        }
    }

    @Test void nativeAuditOutboxAndOwnerCasFailureRollBackTheWholeCaller() {
        submitted(1);
        for (var failure : List.of(failAudit, failOutbox)) {
            failure.set(true);
            assertThrows(IllegalStateException.class, () -> tx().executeWithoutResult(status -> {
                api.withdrawMaterialForOwnerAction(request(NativeOwnerAction.WITHDRAW), 901L, 0L, "failure", "reason"); nativeCas(5);
            }));
            failure.set(false); noWrites();
            assertEquals(3L, jdbc.queryForObject("SELECT version FROM native_owner WHERE id=101", Long.class));
        }
        assertThrows(IllegalStateException.class, () -> tx().executeWithoutResult(status -> {
            api.withdrawMaterialForOwnerAction(request(NativeOwnerAction.WITHDRAW), 901L, 0L, "owner-cas", "reason");
            if (jdbc.update("UPDATE native_owner SET status=5,version=version+1 WHERE id=101 AND version=99") != 1)
                throw new IllegalStateException("Native CAS lost");
        }));
        noWrites();
        assertEquals(1L, run(request(NativeOwnerAction.WITHDRAW), 0, "owner-cas", "reason").version()); anchors();
    }

    @Test void multipleMaterialsFailAtomicallyWithTheNativeTransaction() {
        submitted(1);
        jdbc.update("INSERT INTO plt_delivery_material(id,tenant_id,owner_module,entity_type,entity_id,type_code,status,archive_status,version) VALUES(902,7,'NATIVE','note',101,'DOC','ACTIVE','NOT_REQUIRED',1)");
        assertThrows(BusinessContractException.class, () -> tx().executeWithoutResult(status -> {
            var action = request(NativeOwnerAction.WITHDRAW);
            api.withdrawMaterialForOwnerAction(action, 901L, 0L, "first-material", "reason");
            api.withdrawMaterialForOwnerAction(action, 902L, 0L, "second-material", "reason"); nativeCas(5);
        }));
        noWrites(); assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM plt_delivery_material WHERE id=902", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM native_owner WHERE id=101", Integer.class));
    }

    @Test void concurrentNativeCommandsRevalidateLockedOwnerAndHaveOneWinner() throws Exception {
        submitted(1); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = new ArrayList<Future<Boolean>>();
            for (String key : List.of("native-a", "native-b")) jobs.add(pool.submit(() -> {
                login(7, 9);
                try {
                    start.await();
                    tx().executeWithoutResult(status -> {
                        api.withdrawMaterialForOwnerAction(request(NativeOwnerAction.WITHDRAW), 901L, 0L, key, "reason"); nativeCas(5);
                    }); return true;
                } catch (BusinessContractException denied) { return false; }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear(); }
            }));
            start.countDown(); int winners = 0;
            for (var job : jobs) if (job.get(15, TimeUnit.SECONDS)) winners++;
            assertEquals(1, winners);
        }
        assertEquals(4L, jdbc.queryForObject("SELECT version FROM native_owner WHERE id=101", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM plt_delivery_material WHERE id=901", Long.class));
        assertEquals(1L, counts().get("plt_outbox_event")); assertEquals(1L, counts().get("plt_idempotency_record")); anchors();
    }

    @Configuration(proxyBeanMethods = false) @Import(Config.class)
    static class NativeConfig {
        @Bean @Primary DeliveryOwnerAccess nativeOwners(DataSource source) {
            var jdbc = new JdbcTemplate(source);
            var adapter = new DeliveryMaterialUploadPolicyValidator() {
                public String ownerModule() { return "NATIVE"; }
                public boolean supportsEntityType(String type) { return "note".equals(type); }
                public FileBusinessObjectPolicyFact validateUpload(Long tenant, Long actor, String type, String id, String purpose, String action, boolean lock, Long expected) { throw new AssertionError("Native material withdrawal must not call file UPLOAD"); }
                public Long requireDeliveryAccess(Long tenant, Long actor, String type, String id, String purpose, boolean write, boolean lock, Long expected) {
                    assertTrue(lock); assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    if (!write && downloadOwnerAttempt != null) downloadOwnerAttempt.countDown();
                    var rows = jdbc.queryForList("SELECT tenant_id,writable,version,status FROM native_owner WHERE id=? FOR UPDATE", Long.valueOf(id));
                    if (rows.size() != 1 || !Objects.equals(tenant, ((Number) rows.getFirst().get("tenant_id")).longValue())
                            || write && (!Boolean.TRUE.equals(rows.getFirst().get("writable")) || !Set.of(0, 4).contains(((Number) rows.getFirst().get("status")).intValue())))
                        throw new BusinessContractException("DELIVERY_ACCESS_DENIED", "Generic upload/write Owner denied");
                    return ((Number) rows.getFirst().get("version")).longValue();
                }
                public Long requireNativeOwnerAction(Long tenant, Long actor, String type, String id, String purpose, NativeOwnerAction action, Long expected) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    var rows = jdbc.queryForList("SELECT tenant_id,writable,version,status,scope_visible,project_active FROM native_owner WHERE id=? FOR UPDATE", Long.valueOf(id));
                    if (rows.size() != 1) throw new BusinessContractException("NATIVE_OWNER_DENIED", "Missing native Owner");
                    var row = rows.getFirst(); int state = ((Number) row.get("status")).intValue(); long version = ((Number) row.get("version")).longValue();
                    if (!Objects.equals(tenant, ((Number) row.get("tenant_id")).longValue()) || !Objects.equals(expected, version) || !"DOC".equals(purpose)
                            || !Boolean.TRUE.equals(row.get("scope_visible")) || !Boolean.TRUE.equals(row.get("project_active"))
                            || action == NativeOwnerAction.WITHDRAW && (!nativeWithdraw.get() || !Set.of(1, 2).contains(state))
                            || action == NativeOwnerAction.TERMINATE && (!nativeTerminate.get() || !Set.of(0, 1, 2, 4, 5).contains(state)))
                        throw new BusinessContractException("NATIVE_OWNER_DENIED", "Current native action, scope, state or version denied");
                    return returnInvalidOwnerVersion.get() ? null : version;
                }
            };
            var legacy = new DeliveryMaterialUploadPolicyValidator() {
                public String ownerModule() { return "NATIVE"; }
                public boolean supportsEntityType(String type) { return "legacy".equals(type); }
                public Long requireDeliveryAccess(Long t, Long a, String type, String id, String p, boolean write, boolean lock, Long expected) { return 3L; }
                public FileBusinessObjectPolicyFact validateUpload(Long t, Long a, String e, String id, String p, String action, boolean lock, Long scope) { throw new AssertionError("No native action declaration"); }
            };
            nativeAdapters = new ArrayList<>(List.of(adapter, legacy));
            return new DeliveryOwnerAccess(nativeAdapters, null, null, null, null, null);
        }
    }
}
