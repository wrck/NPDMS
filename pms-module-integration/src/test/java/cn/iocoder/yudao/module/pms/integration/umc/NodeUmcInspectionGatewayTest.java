package cn.iocoder.yudao.module.pms.integration.umc;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NodeUmcInspectionGatewayTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void processLossDoesNotTreatAnEarlierNotSentCheckpointAsCurrentProof() {
        var beforeUpload = new UmcInspectionApi.Checkpoint("input", "75", null, "NOT_SENT");
        var uncertain = NodeUmcInspectionGateway.uncertainCheckpoint(beforeUpload);
        assertEquals("UNKNOWN", uncertain.uploadState());
        assertEquals(beforeUpload.inputDigest(), uncertain.inputDigest());
        assertEquals(beforeUpload.taskId(), uncertain.taskId());
        for (String state : List.of("UNKNOWN", "ACCEPTED")) {
            var checkpoint = new UmcInspectionApi.Checkpoint("input", "75", "690", state);
            assertSame(checkpoint, NodeUmcInspectionGateway.uncertainCheckpoint(checkpoint));
        }
        assertNull(NodeUmcInspectionGateway.uncertainCheckpoint(null));
    }

    @Test void executableStartFailureIsKnownToHaveMadeNoRemoteSubmission() {
        TenantContextHolder.setTenantId(1L);
        var properties = new UmcInspectionProperties();
        properties.setBaseUrl("https://umc.test/"); properties.setUsername("test"); properties.setPassword("synthetic-only");
        properties.setNodeExecutable(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "nonexistent-umc-node-" + java.util.UUID.randomUUID()).toString());
        var request = new UmcInspectionApi.Request(new UmcInspectionApi.Scope(1L, "task", "attempt"), "request",
                List.of(new UmcInspectionApi.OfflineLog("192.0.2.1", new byte[]{1})), null);
        var failure = assertThrows(UmcInspectionApi.Failure.class, () -> new NodeUmcInspectionGateway(properties).inspect(request));
        assertEquals("WORKER_START_FAILED", failure.code());
    }
    @Test void workerReceivesOnlyRuntimeEnvironmentAndItsOwnCredential() {
        var environment = new java.util.HashMap<String, String>();
        environment.put("Path", "runtime-path"); environment.put("SYSTEMROOT", "windows-root");
        environment.put("DATABASE_PASSWORD", "unrelated-secret"); environment.put("SIGNING_KEY", "unrelated-key");
        environment.put("NODE_OPTIONS", "untrusted-hook"); environment.put("NODE_TLS_REJECT_UNAUTHORIZED", "0");
        var properties = new UmcInspectionProperties();
        properties.setBaseUrl("https://umc.test/"); properties.setUsername("test"); properties.setPassword("synthetic-only");
        NodeUmcInspectionGateway.configureEnvironment(environment, properties);
        assertEquals("runtime-path", environment.get("Path"));
        assertEquals("synthetic-only", environment.get("UMC_PASS"));
        for (var key : List.of("DATABASE_PASSWORD", "SIGNING_KEY", "NODE_OPTIONS", "NODE_TLS_REJECT_UNAUTHORIZED"))
            assertFalse(environment.containsKey(key));
    }
    @Test void crossTenantInputIsRejectedBeforeAnyProcessOrNetwork() {
        TenantContextHolder.setTenantId(1L);
        var request = new UmcInspectionApi.Request(new UmcInspectionApi.Scope(2L, "task", "attempt"), "request",
                List.of(new UmcInspectionApi.OfflineLog("192.0.2.1", new byte[]{1})), null);
        var failure = assertThrows(UmcInspectionApi.Failure.class,
                () -> new NodeUmcInspectionGateway(new UmcInspectionProperties()).inspect(request));
        assertEquals("INVALID_INPUT", failure.code());
    }
    @Test void missingConfigurationFailsWithoutStartingAProcess() {
        TenantContextHolder.setTenantId(1L);
        var request = new UmcInspectionApi.Request(new UmcInspectionApi.Scope(1L, "task", "attempt"), "request",
                List.of(new UmcInspectionApi.OfflineLog("192.0.2.1", new byte[]{1})), null);
        var failure = assertThrows(UmcInspectionApi.Failure.class,
                () -> new NodeUmcInspectionGateway(new UmcInspectionProperties()).inspect(request));
        assertEquals("NOT_CONFIGURED", failure.code());
    }
    @Test void externalIdsAndEmptyValuesAreNotReinterpretedAsPlatformResults() {
        var report = JsonUtils.parseObject("""
                {"id":9007199254740993,"taskid":9007199254740995,"devlist":[{"deviceid":1,
                  "rulelist":[{"ruleid":85,"support":true,"error":"","result":false,
                  "targetlist":[{"id":306666,"valuelist":[]}]}]}]}
                """, UmcInspectionApi.Report.class);
        assertEquals("9007199254740993", report.id());
        assertEquals("9007199254740995", report.taskid());
        var rule = report.devlist().getFirst().rulelist().getFirst();
        assertTrue(rule.support()); assertFalse(rule.result());
        assertTrue(rule.targetlist().getFirst().valuelist().isEmpty());
    }
    @Test void wordMlIsValidatedSeparatelyWithoutRelaxingLegacyDocValidation() throws Exception {
        var valid = """
                <?xml version="1.0"?><w:wordDocument xmlns:w="http://schemas.microsoft.com/office/word/2003/wordml"><w:body/></w:wordDocument>
                """;
        NodeUmcInspectionGateway.validateWordXml(valid.getBytes(StandardCharsets.UTF_8));
        for (String invalid : List.of("<html/>", valid.replace("</w:wordDocument>", ""),
                valid.replace("<w:wordDocument", "<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///not-read'>]><w:wordDocument")))
            assertThrows(Exception.class, () -> NodeUmcInspectionGateway.validateWordXml(invalid.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void returnedBinaryEvidenceIsDefensivelyCopied() {
        byte[] doc = {1}, json = {2};
        var result = new UmcInspectionApi.Result(null, "report.doc", "application/msword", "digest", doc, "digest", json, null, List.of());
        doc[0] = 8; json[0] = 9; result.doc()[0] = 7;
        assertEquals(1, result.doc()[0]); assertEquals(2, result.rawDetail()[0]);
    }
}
