package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommerceDeliveryScopeAuditContractTest {

    private static cn.hutool.json.JSONObject contract;

    @BeforeAll
    static void loadContract() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repositoryDirectory = Files.exists(moduleDirectory.resolve("specs"))
                ? moduleDirectory : moduleDirectory.resolve("..").normalize();
        contract = JSONUtil.parseObj(Files.readString(repositoryDirectory.resolve(
                "specs/features/F-COM-001-delivery-scope-audit-contract.json"), StandardCharsets.UTF_8));
    }

    @Test
    void locksExactTopLevelAndLineKeys() {
        assertEquals(List.of("schemaVersion", "action", "projectId", "actorId", "correlationId", "occurredAt",
                        "expectedScopeVersion", "resultScopeVersion", "protectedAsConflict", "lines"),
                contract.getJSONObject("detailSnapshot").getJSONArray("exactKeys").toList(String.class));
        assertEquals(List.of("orderLineId", "sourceKey", "sourceVersion", "beforeQuantity", "requestedQuantity",
                        "afterQuantity", "preservedConflictQuantity", "unitCode", "locationResolutionStatuses",
                        "resultState", "conflictReason", "affectedScopeIds", "serialCount"),
                contract.getJSONObject("DeliveryScopeCommandAuditLine").getJSONArray("exactKeys")
                        .toList(String.class));
    }

    @Test
    void locksStateUnionAndConflictEquivalence() {
        var union = contract.getJSONObject("stateUnion");
        assertTrue(union.getStr("ACTIVE").contains("conflictReason = null"));
        assertTrue(union.getStr("RELEASED").contains("afterQuantity = 0"));
        assertTrue(union.getStr("CONFLICT").contains("preservedConflictQuantity > 0"));
        assertTrue(contract.getStr("commandInvariant").contains("if and only if"));
    }

    @Test
    void forbidsSensitiveOrAmbiguousAuditContent() {
        List<String> forbidden = contract.getJSONArray("privacy").toList(String.class);
        assertEquals(List.of("full serial list forbidden", "ERP source payload forbidden",
                "attachment body forbidden", "extra keys forbidden"), forbidden);
    }
}
