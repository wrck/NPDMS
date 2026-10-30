package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionReportRevisionDO;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InspectionUmcReportResultsTest {
    private final InspectionReportRevisionDO row = new InspectionReportRevisionDO();
    private static final String RESULT = """
            {"id":"9007199254740995","taskid":"9007199254740993","name":"test report","doc":true,
             "devlist":[{"id":"1","deviceip":"192.0.2.10","sn":"TEST-SN","rulelist":[
               {"id":"2","ruleid":"85","support":true,"error":"","result":false,"targetlist":[
                 {"id":"3","name":"empty metrics","result":false,"valuelist":[]},
                 {"id":"4","name":"empty value","valuelist":[{"id":"5","targetid":"4","object":"cpu","value":"","result":true}]}
               ]},
               {"id":"6","ruleid":"86","support":false,"error":"unsupported","targetlist":null}
             ]}]}
            """;
    @BeforeEach void setUp() {
        row.setId(20L); row.setTenantId(7L); row.setTaskId(10L); row.setVersion(1L);

        row.setSourceCode("UMC"); row.setStatusCode("GENERATING");
        row.setSourceContextJson(JsonUtils.toJsonString(new InspectionUmcReportResults.Source(30L, "192.0.2.10", "TEST-SN", true, null,
                new InspectionUmcReportResults.StoredLog(40L, 1, "stored-log", "a".repeat(64)))));
    }

    @Test void savesOnlyTheReportResultSnapshotAndReloadsItsTypedStructure() {
        var result = result(); InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(row, result));
        assertNotNull(row.getResultJson()); assertNotNull(row.getResultSha256());
        assertEquals(result.detail(), InspectionUmcReportResults.load(row));
        var rules = InspectionUmcReportResults.load(row).devlist().getFirst().rulelist();
        assertEquals("9007199254740995", InspectionUmcReportResults.load(row).id());
        assertFalse(rules.getFirst().result()); assertTrue(rules.getFirst().support());
        assertTrue(rules.getFirst().targetlist().getFirst().valuelist().isEmpty());
        assertEquals("", rules.getFirst().targetlist().get(1).valuelist().getFirst().value());
        assertFalse(rules.get(1).support()); assertNull(rules.get(1).targetlist());
    }

    @Test void executionViewReturnsEveryRuleAndNestedValueFromTheOriginalStoredSnapshot() {
        var execution = bindExecution();
        InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(row, result())); row.setStatusCode("AVAILABLE");
        var views = InspectionUmcReportResults.executionResults(new InspectionUmcReportResults.ExecutionResults(7L, 10L, List.of(execution), List.of(row)));
        assertEquals(1, views.size()); assertEquals(50L, views.getFirst().executionId());
        var rules = views.getFirst().result().rulelist(); assertEquals(2, rules.size());
        assertEquals(InspectionUmcReportResults.load(row).devlist().getFirst().rulelist(), rules);
        assertFalse(rules.get(1).support()); assertEquals("unsupported", rules.get(1).error());
        assertEquals("", rules.getFirst().targetlist().get(1).valuelist().getFirst().value());
        assertTrue(rules.getFirst().targetlist().getFirst().valuelist().isEmpty());
    }

    @Test void executionViewRejectsAnotherTenantOrAChangedExecutionLogBinding() {
        var execution = bindExecution();
        assertThrows(InspectionException.class, () -> InspectionUmcReportResults.executionResults(
                new InspectionUmcReportResults.ExecutionResults(8L, 10L, List.of(execution), List.of(row))));
        execution.setFileReferenceKey("another-log");
        assertThrows(InspectionException.class, () -> InspectionUmcReportResults.executionResults(
                new InspectionUmcReportResults.ExecutionResults(7L, 10L, List.of(execution), List.of(row))));
    }

    @Test void pendingExecutionResultIsReturnedAsPendingWithoutInventedRules() {
        var execution = bindExecution();
        var views = InspectionUmcReportResults.executionResults(new InspectionUmcReportResults.ExecutionResults(7L, 10L, List.of(execution), List.of(row)));
        assertEquals("GENERATING", views.getFirst().statusCode()); assertNull(views.getFirst().result());
    }

    @Test void availableExecutionViewsDoNotPresentAnOldFailureAsCurrent() {
        var execution = bindExecution();
        row.setFailureMessage("UMC处理或保存失败（NOT_CONFIGURED）");
        var query = new InspectionUmcReportResults.ExecutionResults(7L, 10L, List.of(execution), List.of(row));
        assertEquals(row.getFailureMessage(), InspectionUmcReportResults.executionResults(query).getFirst().failureMessage());
        InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(row, result())); row.setStatusCode("AVAILABLE");
        assertNull(InspectionUmcReportResults.executionResults(query).getFirst().failureMessage());
        assertEquals("UMC处理或保存失败（NOT_CONFIGURED）", row.getFailureMessage(), "Read projection must not rewrite an immutable historical report");
    }

    private cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionExecutionDO bindExecution() {
        row.setAttemptId(60L);
        var old = InspectionUmcReportResults.source(row);
        InspectionUmcReportResults.updateSource(new InspectionUmcReportResults.SourceUpdate(row,
                new InspectionUmcReportResults.Source(old.deviceId(), old.host(), old.expectedSerial(), old.dispatched(), old.checkpoint(), old.log(), 50L)));
        var execution = new cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionExecutionDO();
        execution.setId(50L); execution.setTenantId(7L); execution.setTaskId(10L); execution.setAttemptId(60L); execution.setDeviceId(30L);
        execution.setArtifactId(40L); execution.setFileVersionNo(1); execution.setFileReferenceKey("stored-log"); execution.setFileSha256("a".repeat(64));
        return execution;
    }

    @Test void acceptsDatabaseJsonWhitespaceAndKeyOrderButRejectsChangedResults() {
        InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(row, result()));
        row.setResultJson(JsonUtils.toJsonString(JsonUtils.parseObject(row.getResultJson(), java.util.TreeMap.class)));
        assertEquals(result().detail(), InspectionUmcReportResults.load(row));
        row.setResultJson(row.getResultJson().replace("TEST-SN", "WRONG-SN"));
        assertThrows(InspectionException.class, () -> InspectionUmcReportResults.load(row));
    }

    @Test void savedResultAndRecoveryMetadataCannotBeOverwritten() {
        InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(row, result()));
        assertThrows(InspectionException.class, () -> InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(row, result())));
        assertThrows(InspectionException.class, () -> InspectionUmcReportResults.updateSource(new InspectionUmcReportResults.SourceUpdate(row,
                new InspectionUmcReportResults.Source(30L, "192.0.2.10", "TEST-SN", false, null, InspectionUmcReportResults.source(row).log()))));
    }

    @Test void existingPlatformOrFinishedReportCannotBeChanged() {
        row.setSourceCode("PLATFORM");
        assertThrows(InspectionException.class, () -> InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(row, result())));
        row.setSourceCode("UMC"); row.setStatusCode("AVAILABLE");
        assertThrows(InspectionException.class, () -> InspectionUmcReportResults.complete(new InspectionUmcReportResults.Completion(row, result())));
        assertNull(row.getResultJson());
    }

    @Test void recoveryCheckpointStaysInDatabaseWithoutAResultFile() {
        var checkpoint = result().checkpoint();
        InspectionUmcReportResults.updateSource(new InspectionUmcReportResults.SourceUpdate(row,
                new InspectionUmcReportResults.Source(30L, "192.0.2.10", "TEST-SN", true, checkpoint, InspectionUmcReportResults.source(row).log())));
        assertEquals(checkpoint, InspectionUmcReportResults.source(row).checkpoint()); assertNull(row.getResultJson());
    }

    private UmcInspectionApi.Result result() {
        byte[] raw = RESULT.getBytes(StandardCharsets.UTF_8), doc = {1};
        return new UmcInspectionApi.Result(new UmcInspectionApi.Checkpoint("input", "9007199254740993", "9007199254740995", "ACCEPTED"),
                "report.doc", "application/vnd.ms-wordml", DigestUtil.sha256Hex(doc), doc, DigestUtil.sha256Hex(raw), raw,
                JsonUtils.parseObject(raw, UmcInspectionApi.Report.class), List.of());
    }
}
