package cn.iocoder.yudao.module.pms.commerce.api;

import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchCommand;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchResult;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceContractFact;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceOrderContractRelationFact;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceOrderLineFact;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSalesOrderFact;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSourceLifecycleStatus;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.AssignedDeliveryScopeLine;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.AssignedDeliveryScopeResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeSliceDTO;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopePreviewCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercePublicContractTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 12, 0);

    @Test
    void extendsDeliveryScopeApiWithoutChangingExistingMethods() throws Exception {
        assertEquals(4, DeliveryScopeApi.class.getDeclaredMethods().length);
        assertEquals(AssignedDeliveryScopeResult.class,
                DeliveryScopeApi.class.getMethod("getAssignedScope", Long.class, Long.class).getReturnType());
        assertTrue(DeliveryScopeApi.class.getMethod("getAssignedScope", Long.class, Long.class).isDefault());
        assertEquals(3, Arrays.stream(DeliveryScopeApi.class.getDeclaredMethods())
                .filter(method -> !method.getName().equals("getAssignedScope"))
                .count());
        assertEquals(CommerceAuthorityBatchResult.class,
                CommerceAuthorityIngestApi.class.getMethod("ingestBatch", CommerceAuthorityBatchCommand.class)
                        .getReturnType());
    }

    @Test
    void failsClosedUntilAssignedScopeProviderIsInstalled() {
        DeliveryScopeApi legacyImplementation = new DeliveryScopeApi() {
            @Override
            public List<DeliveryScopeSliceDTO> getAvailableSlices(Long parentProjectId,
                                                                  Long expectedScopeVersion) {
                return List.of();
            }

            @Override
            public SplitScopeApplyResult previewSplit(SplitScopePreviewCommand command) {
                return null;
            }

            @Override
            public SplitScopeApplyResult applySplit(SplitScopeApplyCommand command) {
                return null;
            }
        };

        DeliveryScopeFactException failure = assertThrows(DeliveryScopeFactException.class,
                () -> legacyImplementation.getAssignedScope(1L, null));
        assertEquals(DeliveryScopeFactException.Code.PROVIDER_UNAVAILABLE, failure.getCode());
    }

    @Test
    void stabilizesAssignedLinesAndNormalizedSerialIdentity() {
        AssignedDeliveryScopeLine later = line(20L, 201L, 2L, "sn-2", "SN-1");
        AssignedDeliveryScopeLine earlier = line(10L, 101L, 1L);

        AssignedDeliveryScopeResult result = new AssignedDeliveryScopeResult(9L, 4L,
                List.of(later, earlier));

        assertEquals(List.of(1L, 2L), result.assignedLines().stream()
                .map(AssignedDeliveryScopeLine::orderLineId).toList());
        assertEquals(List.of("SN-1", "sn-2"), later.serialNumbers());
        assertEquals(BigDecimal.class, component(AssignedDeliveryScopeLine.class, "quantity").getType());
        assertEquals(Long.class, component(AssignedDeliveryScopeResult.class, "scopeVersion").getType());
    }

    @Test
    void rejectsCorruptedAssignedScopeShapes() {
        DeliveryScopeFactException duplicateSerial = assertThrows(DeliveryScopeFactException.class,
                () -> line(10L, 101L, 1L, " sn-1 ", "SN-1"));
        assertEquals(DeliveryScopeFactException.Code.OWNER_DATA_CORRUPTED, duplicateSerial.getCode());

        assertThrows(DeliveryScopeFactException.class,
                () -> new AssignedDeliveryScopeLine(10L, 101L, 1L, BigDecimal.ONE,
                        "EA", null, null, List.of()));
        assertThrows(DeliveryScopeFactException.class,
                () -> new AssignedDeliveryScopeLine(10L, 101L, 1L, BigDecimal.ONE,
                        "EA", "P-1", null, List.of("SN-1", "SN-2")));
        assertThrows(DeliveryScopeFactException.class,
                () -> new AssignedDeliveryScopeResult(9L, 0L,
                        List.of(line(10L, 101L, 1L), line(10L, 101L, 1L))));
    }

    @Test
    void normalizesAndStablyOrdersAuthorityBatchWithoutOrderingOpaqueVersions() {
        CommerceAuthorityBatchCommand command = new CommerceAuthorityBatchCommand(
                1L, " event-1 ", " batch-1 ", " ERP ", "10",
                List.of(contract("C-2", "2"), contract("C-1", "10")),
                List.of(), List.of(), List.of(), NOW, " chain-1 ");

        assertEquals("event-1", command.eventId());
        assertEquals("ERP", command.sourceSystem());
        assertEquals(List.of("C-1", "C-2"), command.contracts().stream()
                .map(CommerceContractFact::sourceKey).toList());
        assertEquals(List.of("10", "2"), command.contracts().stream()
                .map(CommerceContractFact::sourceVersion).toList());
        assertEquals("chain-1", command.correlationId());
    }

    @Test
    void keepsOrderContractRelationIdentityAsTwoIndependentSourceKeys() {
        String orderKey = "O".repeat(128);
        String contractKey = "C".repeat(128);
        CommerceOrderContractRelationFact relation = new CommerceOrderContractRelationFact(
                " " + orderKey + " ", " " + contractKey + " ", null, "v1", NOW, null);
        CommerceAuthorityBatchCommand command = new CommerceAuthorityBatchCommand(
                1L, "event", "batch", "ERP", "wm", List.of(), List.of(), List.of(),
                List.of(relation), NOW, "chain");

        assertEquals(orderKey, command.orderContractRelations().getFirst().salesOrderSourceKey());
        assertEquals(contractKey, command.orderContractRelations().getFirst().contractSourceKey());
        assertThrows(CommerceAuthorityIngestException.class,
                () -> new CommerceAuthorityBatchCommand(1L, "event", "batch", "ERP", "wm",
                        List.of(), List.of(), List.of(), List.of(relation, relation), NOW, "chain"));
    }

    @Test
    void rejectsIncompleteOrDuplicateAuthorityBatchInput() {
        CommerceAuthorityIngestException empty = assertThrows(CommerceAuthorityIngestException.class,
                () -> new CommerceAuthorityBatchCommand(1L, "event", "batch", "ERP", "wm",
                        List.of(), List.of(), List.of(), List.of(), NOW, "chain"));
        assertEquals(CommerceAuthorityIngestException.Code.INVALID_REQUEST, empty.getCode());

        assertThrows(CommerceAuthorityIngestException.class,
                () -> new CommerceAuthorityBatchCommand(1L, "event", "batch", "ERP", "wm",
                        List.of(contract("C-1", "1"), contract("C-1", "2")),
                        List.of(), List.of(), List.of(), NOW, "chain"));
        assertThrows(CommerceAuthorityIngestException.class,
                () -> new CommerceOrderLineFact("L-1", null, "1", "O-1", "10",
                        null, null, BigDecimal.valueOf(-1), null,
                        CommerceSourceLifecycleStatus.ACTIVE, NOW));
        CommerceOrderLineFact pendingAuthority = new CommerceOrderLineFact("L-2", null, "1", "O-1",
                "20", null, null, null, null, CommerceSourceLifecycleStatus.ACTIVE, NOW);
        assertNull(pendingAuthority.quantity());
    }

    @Test
    void exposesOnlyClosedDecisionsAndStableFailureCodes() {
        assertEquals(Set.of("ACCEPTED", "ACCEPTED_NO_CHANGE", "EVENT_REPLAYED"),
                enumNames(CommerceAuthorityBatchResult.Decision.class));
        assertEquals(Set.of("INVALID_REQUEST", "TENANT_CONTEXT_MISMATCH",
                        "PROJECT_NOT_VISIBLE_OR_INELIGIBLE", "SCOPE_STALE", "SCOPE_CONFLICT",
                        "OWNER_DATA_CORRUPTED", "PROVIDER_UNAVAILABLE"),
                enumNames(DeliveryScopeFactException.Code.class));
        assertEquals(Set.of("INVALID_REQUEST", "TENANT_CONTEXT_MISMATCH", "EVENT_PAYLOAD_CONFLICT",
                        "SOURCE_VERSION_CONFLICT", "SOURCE_VERSION_PAYLOAD_CONFLICT",
                        "OWNER_DATA_CORRUPTED", "PROVIDER_UNAVAILABLE"),
                enumNames(CommerceAuthorityIngestException.Code.class));
    }

    @Test
    void publicRecordsDoNotExposeDataObjectsOrSourcePayloadBodies() {
        List<Class<?>> records = List.of(AssignedDeliveryScopeResult.class, AssignedDeliveryScopeLine.class,
                CommerceAuthorityBatchCommand.class, CommerceAuthorityBatchResult.class,
                CommerceContractFact.class, CommerceSalesOrderFact.class, CommerceOrderLineFact.class,
                CommerceOrderContractRelationFact.class);
        for (Class<?> type : records) {
            assertTrue(type.isRecord());
            for (RecordComponent component : type.getRecordComponents()) {
                assertFalse(component.getType().getName().contains("dal.dataobject"));
                assertFalse(Set.of("sourcePayload", "sourceBody", "rawPayload").contains(component.getName()));
            }
        }
    }

    private static AssignedDeliveryScopeLine line(Long scopeId, Long detailId, Long orderLineId,
                                                  String... serials) {
        BigDecimal quantity = serials.length == 0 ? new BigDecimal("2.500000")
                : BigDecimal.valueOf(serials.length);
        return new AssignedDeliveryScopeLine(scopeId, detailId, orderLineId, quantity,
                "EA", "P-1", null, List.of(serials));
    }

    private static CommerceContractFact contract(String sourceKey, String sourceVersion) {
        return new CommerceContractFact(sourceKey, null, sourceVersion, "COMP", "NO-" + sourceKey,
                null, null, null, null, CommerceSourceLifecycleStatus.ACTIVE, NOW);
    }

    private static RecordComponent component(Class<?> type, String name) {
        return Arrays.stream(type.getRecordComponents()).filter(item -> item.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }
}
