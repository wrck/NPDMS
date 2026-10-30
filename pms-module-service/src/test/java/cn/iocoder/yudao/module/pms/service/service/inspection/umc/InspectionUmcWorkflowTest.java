package cn.iocoder.yudao.module.pms.service.service.inspection.umc;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InspectionUmcWorkflowTest {
    private final InspectionAccessService access = mock(InspectionAccessService.class);
    private final InspectionTaskService tasks = mock(InspectionTaskService.class);
    private final InspectionAttemptMapper attempts = mock(InspectionAttemptMapper.class);
    private final InspectionExecutionMapper executions = mock(InspectionExecutionMapper.class);
    private final InspectionTaskDeviceMapper devices = mock(InspectionTaskDeviceMapper.class);
    private final InspectionDetectionResultMapper results = mock(InspectionDetectionResultMapper.class);
    private final InspectionReportRevisionMapper reports = mock(InspectionReportRevisionMapper.class);
    private final InspectionReportResultMapper links = mock(InspectionReportResultMapper.class);
    private final InspectionUmcFormalResults facts = mock(InspectionUmcFormalResults.class);
    private final InspectionUmcBundle bundles = mock(InspectionUmcBundle.class);
    private final InspectionRequestContext context = new InspectionRequestContext(7L,9L,"operation","test",1L);
    private final InspectionTaskDO task = new InspectionTaskDO();
    private final InspectionAttemptDO attempt = new InspectionAttemptDO();
    private final List<InspectionDetectionResultDO> stored = new ArrayList<>();
    private final InspectionExecutionDO execution = new InspectionExecutionDO();
    private final InspectionReportRevisionDO report = new InspectionReportRevisionDO();
    private InspectionUmcWorkflow workflow;
    @BeforeEach void setup() {
        workflow = new InspectionUmcWorkflow(access,tasks,attempts,executions,devices,results,reports,links,facts,bundles);
        task.setId(10L);task.setTenantId(7L);task.setVersion(1L);task.setMode("OFFLINE");task.setStatusCode("EXECUTING");task.setCurrentAttemptId(30L);
        attempt.setId(30L);attempt.setTenantId(7L);attempt.setTaskId(10L);attempt.setProviderCode("UMC");attempt.setMode("OFFLINE");attempt.setStatusCode("ACTIVE");attempt.setAttemptNo(1);
        execution.setId(40L);execution.setTenantId(7L);execution.setTaskId(10L);execution.setAttemptId(30L);execution.setDeviceId(50L);execution.setStatusCode("PENDING");
        report.setId(60L);report.setTenantId(7L);report.setTaskId(10L);report.setAttemptId(30L);report.setSourceCode("UMC");report.setStatusCode("AVAILABLE");report.setRevisionNo(1);
        report.setSourceContextJson(JsonUtils.toJsonString(new InspectionUmcReportResults.Source(50L,"192.0.2.1","QA",true,null,new InspectionUmcReportResults.StoredLog(100L,1,"log","a".repeat(64)),40L)));
        when(access.task(any())).thenReturn(task);when(attempts.selectById(30L)).thenReturn(attempt);
        when(attempts.selectForTask(any())).thenReturn(List.of(attempt));when(executions.selectForTask(any())).thenReturn(List.of(execution));when(executions.selectById(40L)).thenReturn(execution);
        when(attempts.updateById(any(InspectionAttemptDO.class))).thenReturn(1);
        var device = new InspectionTaskDeviceDO();device.setDeviceId(50L);when(devices.selectForTask(any())).thenReturn(List.of(device));
        when(reports.selectUmcSources(any())).thenReturn(List.of(report));when(reports.selectForTask(any())).thenReturn(List.of(report));
        when(reports.selectContent(argThat(q -> q.tenantId().equals(7L) && q.taskId().equals(10L) && q.reportId().equals(60L)))).thenReturn(report);
        when(results.selectForTask(any())).thenReturn(stored);when(executions.updateById(any(InspectionExecutionDO.class))).thenReturn(1);when(reports.updateById(any(InspectionReportRevisionDO.class))).thenReturn(1);
        doAnswer(inv -> { var row = inv.<InspectionDetectionResultDO>getArgument(0);row.setId(1000L+stored.size());stored.add(row);return 1; }).when(results).insert(any(InspectionDetectionResultDO.class));
        doAnswer(inv -> { inv.<InspectionReportRevisionDO>getArgument(0).setId(70L);return 1; }).when(reports).insert(any(InspectionReportRevisionDO.class));
        when(bundles.available(any())).thenReturn(true);
        observe(false,true,null);
    }
    private void observe(Boolean result, Boolean support, String error) {
        var rule = JsonUtils.parseObject("{\"id\":\"r1\",\"ruleid\":\"1\",\"name\":\"UMC检查\",\"support\":"+support+",\"result\":"+result+",\"error\":"+(error==null?"null":"\""+error+"\"")+"}",UmcInspectionApi.Rule.class);
        var device = JsonUtils.parseObject("{\"id\":\"d1\",\"sn\":\"QA\",\"rulelist\":["+JsonUtils.toJsonString(rule)+"]}",UmcInspectionApi.Device.class);
        when(facts.observation(report)).thenReturn(new InspectionUmcFormalResults.Observation(report,execution,device));when(facts.rule(any())).thenReturn(rule);
    }
    @Test void batchFilenameCanSupplyIpOnlyForOneTaskDeviceAndMatchingUmcScope() {
        when(attempts.selectById(30L)).thenReturn(attempt);
        var device = new InspectionTaskDeviceDO(); device.setDeviceId(41L);
        when(devices.selectForTask(any())).thenReturn(List.of(device));
        var named = new InspectionUmcWorkflow.NamedUpload(context, 10L, 30L, "name(192.0.2.10)");
        assertEquals(List.of(new InspectionUmcService.Host(41L, "192.0.2.10")), workflow.inferBatchUploadSources(named));
        when(devices.selectForTask(any())).thenReturn(List.of(device, new InspectionTaskDeviceDO()));
        assertNull(workflow.inferBatchUploadSources(named));
        attempt.setTenantId(8L); assertNull(workflow.inferBatchUploadSources(named));
        attempt.setTenantId(7L); attempt.setProviderCode("PLATFORM"); assertNull(workflow.inferBatchUploadSources(named));
    }
    @Test void captureReadsTheStoredSnapshotInsteadOfTheMetadataProjection() {
        var metadata = new InspectionReportRevisionDO(); metadata.setId(60L); metadata.setStatusCode("AVAILABLE");
        when(reports.selectById(60L)).thenReturn(metadata);
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        assertEquals(1, stored.size()); assertEquals("COMPLETE",execution.getStatusCode());
    }
    @Test void uploadedAvailableResultsAreRegisteredWithoutASecondManualFetch() {
        execution.setPurposeCode("OFFLINE");execution.setOriginalFileSha256("upload-digest");
        when(reports.selectUmcExecutionResults(any())).thenReturn(List.of(report));
        workflow.captureUploaded(new InspectionUmcWorkflow.Uploaded(context,10L,"upload-digest"));
        assertEquals("COMPLETE",execution.getStatusCode());assertEquals(1,stored.size());assertEquals("UMC",stored.getFirst().getSourceCode());
        workflow.captureUploaded(new InspectionUmcWorkflow.Uploaded(context,10L,"upload-digest"));assertEquals(1,stored.size());
    }
    @Test void failedOrUnrelatedUploadsNeverBecomeFormalCompletedExecutions() {
        execution.setPurposeCode("OFFLINE");execution.setOriginalFileSha256("upload-digest");
        report.setStatusCode("GENERATING");when(reports.selectUmcExecutionResults(any())).thenReturn(List.of(report));
        workflow.captureUploaded(new InspectionUmcWorkflow.Uploaded(context,10L,"upload-digest"));assertTrue(stored.isEmpty());
        report.setStatusCode("AVAILABLE");workflow.captureUploaded(new InspectionUmcWorkflow.Uploaded(context,10L,"other-digest"));assertTrue(stored.isEmpty());
        assertEquals("PENDING",execution.getStatusCode());
    }
    @Test void umcUploadWithoutSourceIpIsRejectedBeforeAnyLogIsStored() {
        assertThrows(InspectionException.class,()->workflow.validateUploadSource(new InspectionUmcWorkflow.UploadSource(context,10L,30L,false)));
        assertDoesNotThrow(()->workflow.validateUploadSource(new InspectionUmcWorkflow.UploadSource(context,10L,30L,true)));
        attempt.setProviderCode("PLATFORM");
        assertDoesNotThrow(()->workflow.validateUploadSource(new InspectionUmcWorkflow.UploadSource(context,10L,30L,false)));
        verify(results,never()).insert(any(InspectionDetectionResultDO.class));
    }
    @Test void filenameSourceInferenceIsBoundToTheSameTenantTaskAndUmcAttempt() {
        var request = new InspectionUmcWorkflow.NamedUpload(context, 10L, 30L, "192.0.2.10");
        assertEquals("192.0.2.10", workflow.inferUploadSource(request));
        attempt.setProviderCode("PLATFORM"); assertNull(workflow.inferUploadSource(request));
        attempt.setProviderCode("UMC"); attempt.setTenantId(8L); assertNull(workflow.inferUploadSource(request));
        attempt.setTenantId(7L); attempt.setTaskId(11L); assertNull(workflow.inferUploadSource(request));
        attempt.setTaskId(10L); assertNull(workflow.inferUploadSource(new InspectionUmcWorkflow.NamedUpload(context, 10L, 30L, "capture.log")));
        verifyNoInteractions(tasks);
    }
    @Test void registersOnlyFormalReferencesAndRetryDoesNotDuplicateOrInventRuleRevisions() {
        var fetch = new InspectionUmcWorkflow.Fetch(context,10L,40L);workflow.capture(fetch);workflow.capture(fetch);
        assertEquals(1,stored.size());var row=stored.getFirst();assertEquals("PASS",row.getOutcome());assertEquals(60L,row.getSourceReportId());
        assertEquals("r1",row.getExternalRuleId());assertNull(row.getRuleRevisionId());assertNull(row.getRuleSnapshotId());assertNull(row.getOutputSummary());
        assertEquals("COMPLETE",execution.getStatusCode());verify(results,times(1)).insert(any(InspectionDetectionResultDO.class));verify(tasks,times(1)).save(any());
    }
    @Test void unsupportedReportCompletesWithoutRewritingItsFormalOutcome() {
        observe(false,false,"unsupported");workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        assertEquals("UNJUDGED",stored.getFirst().getOutcome());assertEquals("COMPLETE",execution.getStatusCode());
        assertDoesNotThrow(()->workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null)));
    }
    @Test void returnedExecutionErrorsAndUnjudgedRulesDoNotEraseSuccessfulReportRetrieval() {
        observe(null,true,"command failed");workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        assertEquals("ERROR",stored.getFirst().getOutcome());assertEquals("COMPLETE",execution.getStatusCode());
        assertDoesNotThrow(()->workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null)));
    }
    @Test void unjudgedResultStaysUnjudgedWhileTheReportCompletesAndClearsOldIncompleteDiagnostic() {
        observe(null,true,null);execution.setStatusCode("PARTIAL");execution.setFailureCode("UMC_RESULT_INCOMPLETE");
        execution.setFailureMessage("old result gate");workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        assertEquals("UNJUDGED",stored.getFirst().getOutcome());assertEquals("COMPLETE",execution.getStatusCode());
        assertEquals("",execution.getFailureCode());assertEquals("",execution.getFailureMessage());
        assertDoesNotThrow(()->workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null)));
    }
    @Test void validZeroRuleReportCompletesAndAdoptsTheOriginalDocWithoutInventedResults() {
        var device=JsonUtils.parseObject("{\"id\":\"d1\",\"sn\":\"QA\",\"rulelist\":[]}",UmcInspectionApi.Device.class);
        when(facts.observation(report)).thenReturn(new InspectionUmcFormalResults.Observation(report,execution,device));
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        assertEquals("COMPLETE",execution.getStatusCode());assertTrue(stored.isEmpty());
        workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null));task.setStatusCode("REPORT_PENDING");
        workflow.adopt(new InspectionUmcWorkflow.Command(context,10L,"zero rules"));
        verify(reports).insert(argThat((InspectionReportRevisionDO row)->InspectionUmcBundle.snapshot(row).reportIds().equals(List.of(60L))
                && InspectionUmcBundle.snapshot(row).resultIds().isEmpty()));
        verify(links,never()).insert(any(InspectionReportResultDO.class));
    }
    @Test void missingRuleListRemainsInvalidAndCreatesNoFormalResults() {
        var device=JsonUtils.parseObject("{\"id\":\"d1\",\"sn\":\"QA\"}",UmcInspectionApi.Device.class);
        when(facts.observation(report)).thenReturn(new InspectionUmcFormalResults.Observation(report,execution,device));
        assertThrows(InspectionException.class,()->workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L)));
        assertTrue(stored.isEmpty());assertEquals("PENDING",execution.getStatusCode());
    }
    @Test void previouslyPartialExecutionCanFinishFromItsSavedCompleteReportWithoutFetchingOrRewritingHistory() {
        observe(false,false,"unsupported");workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        execution.setStatusCode("PARTIAL");clearInvocations(executions,results);
        assertDoesNotThrow(()->workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null)));
        assertEquals("PARTIAL",execution.getStatusCode());assertEquals("UNJUDGED",stored.getFirst().getOutcome());
        verify(executions,never()).updateById(any(InspectionExecutionDO.class));
        verify(results,never()).updateById(any(InspectionDetectionResultDO.class));
    }
    @Test void substitutedRuleIdsCannotPassEvenWhenTheNumberOfResultsMatches() {
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));stored.getFirst().setExternalRuleId("other-rule");
        assertThrows(InspectionException.class,()->workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null)));
        verify(tasks,never()).transition(any());
    }
    @Test void completedExecutionCannotFinishWhenItsSourceReportIsNoLongerAvailable() {
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));report.setStatusCode("FAILED");
        assertThrows(InspectionException.class,()->workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null)));
        verify(tasks,never()).transition(any());
    }
    @Test void pendingReportDoesNotCreateFormalJudgement() {
        report.setStatusCode("GENERATING");workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        assertTrue(stored.isEmpty());assertEquals("PENDING",execution.getStatusCode());verifyNoInteractions(facts);
    }
    @Test void missingDeviceBlocksEvenWhenAnotherDeviceIsComplete() {
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));var other = new InspectionTaskDeviceDO();other.setDeviceId(51L);
        var original = new InspectionTaskDeviceDO();original.setDeviceId(50L);when(devices.selectForTask(any())).thenReturn(List.of(original,other));
        assertThrows(InspectionException.class,()->workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null)));verify(tasks,never()).transition(any());
    }
    @Test void adoptsSourceReferencesAndUsesOriginalStateMachineWithRealFormalResultIds() {
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null));
        verify(tasks).transition(argThat(c->c.transition().action()==cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine.Action.EXECUTION_COMPLETE && c.transition().executionComplete()));
        assertEquals("COMPLETED",attempt.getStatusCode());task.setStatusCode("REPORT_PENDING");workflow.adopt(new InspectionUmcWorkflow.Command(context,10L,"all devices"));
        assertEquals(70L,task.getCurrentReportId());verify(links).insert(argThat((InspectionReportResultDO row)->row.getReportId().equals(70L) && row.getResultId().equals(stored.getFirst().getId())));
        verify(reports).insert(argThat((InspectionReportRevisionDO row)->InspectionUmcBundle.snapshot(row).reportIds().equals(List.of(60L))));
    }
    @Test void adoptionRejectsForeignTaskOrTenantRowsBeforeWritingAnyAssociation() {
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        task.setStatusCode("REPORT_PENDING");attempt.setStatusCode("COMPLETED");
        stored.getFirst().setTenantId(8L);stored.getFirst().setTaskId(11L);
        assertThrows(InspectionException.class,()->workflow.adopt(new InspectionUmcWorkflow.Command(context,10L,null)));
        verify(links,never()).insert(any(InspectionReportResultDO.class));
    }
    @Test void adoptionRejectsAnAlreadyFrozenReportBeforeWritingAnyAssociation() {
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        task.setStatusCode("REPORT_PENDING");attempt.setStatusCode("COMPLETED");
        doAnswer(inv->{var row=inv.<InspectionReportRevisionDO>getArgument(0);row.setId(70L);row.setStatusCode("AVAILABLE");return 1;})
                .when(reports).insert(any(InspectionReportRevisionDO.class));
        assertThrows(InspectionException.class,()->workflow.adopt(new InspectionUmcWorkflow.Command(context,10L,null)));
        verify(links,never()).insert(any(InspectionReportResultDO.class));
    }
    @Test void cancelledOrForeignExecutionIsNeverConsumed() {
        execution.setStatusCode("CANCELLED");assertThrows(InspectionException.class,()->workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L)));
        assertTrue(stored.isEmpty());verifyNoInteractions(facts);
    }
    @Test void newProviderDoesNotSilentlyReplaceActiveOldLogsOrEnableOnline() {
        assertThrows(InspectionException.class,()->workflow.start(new InspectionUmcWorkflow.Command(context,10L,"keep history")));
        task.setMode("ONLINE");assertThrows(InspectionException.class,()->workflow.start(new InspectionUmcWorkflow.Command(context,10L,"online later")));
        verify(attempts,never()).insert(any(InspectionAttemptDO.class));
    }
    @Test void aPlatformAttemptCannotBeRelabelledByTheUmcResultOrCompletionActions() {
        attempt.setProviderCode("PLATFORM");
        assertThrows(InspectionException.class,()->workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L)));
        assertThrows(InspectionException.class,()->workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null)));
        verify(results,never()).insert(any(InspectionDetectionResultDO.class));verify(tasks,never()).transition(any());
        verify(attempts,never()).updateById(any(InspectionAttemptDO.class));
    }
    @Test void onlineStartFreezesCollectionCommandsAndKeepsUmcAsTheProvider() {
        execution.setStatusCode("CANCELLED");task.setMode("ONLINE");attempt.setProviderCode("PLATFORM");
        doAnswer(inv->{inv.<InspectionAttemptDO>getArgument(0).setId(31L);return 1;}).when(attempts).insert(any(InspectionAttemptDO.class));
        workflow.startOnline(new InspectionUmcWorkflow.Command(context,10L,"collect with DAC, judge with UMC"));
        verify(tasks).freezeCollectionCommands(argThat(c->"UMC".equals(c.attempt().getProviderCode())&&"ONLINE".equals(c.attempt().getMode())));
        verify(tasks).transition(argThat(c->c.transition().action()==cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine.Action.START_ONLINE));
        assertEquals("PLATFORM",attempt.getProviderCode());
    }
    @Test void aPassedPrecheckDoesNotReplaceOrHideTheCollectedUmcJudgement() {
        task.setMode("ONLINE");attempt.setMode("ONLINE");execution.setPurposeCode("COLLECT");
        workflow.capture(new InspectionUmcWorkflow.Fetch(context,10L,40L));
        var precheck=new InspectionExecutionDO();precheck.setId(41L);precheck.setAttemptId(30L);precheck.setDeviceId(50L);
        precheck.setPurposeCode("PRECHECK");precheck.setStatusCode("PASSED");
        when(executions.selectForTask(any())).thenReturn(List.of(execution,precheck));
        workflow.finish(new InspectionUmcWorkflow.Command(context,10L,null));assertEquals("COMPLETED",attempt.getStatusCode());
    }
    @Test void startCreatesANewUmcIdentityWithoutRewritingThePreviousProvider() {
        execution.setStatusCode("CANCELLED");attempt.setProviderCode("PLATFORM");
        doAnswer(inv->{inv.<InspectionAttemptDO>getArgument(0).setId(31L);return 1;}).when(attempts).insert(any(InspectionAttemptDO.class));
        workflow.start(new InspectionUmcWorkflow.Command(context,10L,"use UMC with preserved history"));
        assertEquals("PLATFORM",attempt.getProviderCode());assertEquals("SUPERSEDED",attempt.getStatusCode());assertEquals(31L,task.getCurrentAttemptId());
        verify(attempts).insert(argThat((InspectionAttemptDO row)->"UMC".equals(row.getProviderCode()) && "OFFLINE".equals(row.getMode())));
    }
}
