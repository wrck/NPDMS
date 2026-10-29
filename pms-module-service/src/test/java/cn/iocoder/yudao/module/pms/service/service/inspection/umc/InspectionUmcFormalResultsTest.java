package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectionUmcFormalResultsTest {
    @Test void untrustedOrIncompleteConclusionsAreNeverPass() {
        for (var json : new String[]{"{\"support\":false,\"result\":false}","{\"result\":false}","{\"support\":true}"})
            assertEquals("UNJUDGED",InspectionUmcFormalResults.outcome(JsonUtils.parseObject(json,UmcInspectionApi.Rule.class)));
        assertEquals("ERROR",InspectionUmcFormalResults.outcome(JsonUtils.parseObject("{\"support\":true,\"result\":false,\"error\":\"failure\"}",UmcInspectionApi.Rule.class)));
        assertEquals("ABNORMAL",InspectionUmcFormalResults.outcome(JsonUtils.parseObject("{\"support\":true,\"result\":true}",UmcInspectionApi.Rule.class)));
    }
    @Test void formalRuleMustMatchPersistedTenantExecutionAndCanonicalSnapshot() {
        var reports=mock(InspectionReportRevisionMapper.class);var executions=mock(InspectionExecutionMapper.class);
        var facts=new InspectionUmcFormalResults(reports,executions);var report=new InspectionReportRevisionDO();
        report.setId(60L);report.setTenantId(7L);report.setTaskId(10L);report.setAttemptId(30L);report.setSourceCode("UMC");report.setStatusCode("AVAILABLE");
        report.setSourceContextJson(JsonUtils.toJsonString(new InspectionUmcReportResults.Source(50L,"192.0.2.1","QA",true,null,new InspectionUmcReportResults.StoredLog(500L,1,"log","a".repeat(64)),40L)));
        var detail=JsonUtils.parseObject("{\"id\":\"100\",\"devlist\":[{\"id\":\"d1\",\"sn\":\"QA\",\"deviceip\":\"192.0.2.1\",\"rulelist\":[{\"id\":\"9007199254740995\",\"ruleid\":\"1\",\"level\":2,\"support\":true,\"result\":true}]}]}",UmcInspectionApi.Report.class);
        report.setResultJson(JsonUtils.toJsonString(detail));report.setResultSha256(DigestUtil.sha256Hex(report.getResultJson()));
        var execution=new InspectionExecutionDO();execution.setId(40L);execution.setTenantId(7L);execution.setTaskId(10L);execution.setAttemptId(30L);execution.setDeviceId(50L);
        execution.setArtifactId(500L);execution.setFileVersionNo(1);execution.setFileReferenceKey("log");execution.setFileSha256("a".repeat(64));
        when(reports.selectContent(argThat(q -> q.tenantId().equals(7L) && q.taskId().equals(10L) && q.reportId().equals(60L)))).thenReturn(report);when(executions.selectById(40L)).thenReturn(execution);
        var row=new InspectionDetectionResultDO();row.setTenantId(7L);row.setTaskId(10L);row.setAttemptId(30L);row.setDeviceId(50L);row.setExecutionId(40L);
        row.setSourceCode("UMC");row.setSourceReportId(60L);row.setExternalRuleId("9007199254740995");row.setOutcome("ABNORMAL");
        assertEquals("9007199254740995",facts.rule(row).id());assertEquals("UMC_LEVEL_2",InspectionUmcFormalResults.severity(facts.rule(row)));
        row.setExternalRuleId("absent");assertThrows(InspectionException.class,()->facts.rule(row));row.setExternalRuleId("9007199254740995");
        row.setAttemptId(31L);assertThrows(InspectionException.class,()->facts.rule(row));row.setAttemptId(30L);
        row.setSourceReportId(999L);assertThrows(InspectionException.class,()->facts.rule(row));row.setSourceReportId(60L);
        row.setOutcome("PASS");assertThrows(InspectionException.class,()->facts.rule(row));row.setOutcome("ABNORMAL");
        execution.setTenantId(8L);assertThrows(InspectionException.class,()->facts.rule(row));execution.setTenantId(7L);
        report.setResultSha256("b".repeat(64));assertThrows(InspectionException.class,()->facts.rule(row));
    }
}
