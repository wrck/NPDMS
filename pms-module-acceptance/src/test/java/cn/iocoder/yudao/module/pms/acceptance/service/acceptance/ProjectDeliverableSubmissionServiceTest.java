package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectDeliverableSubmissionServiceTest {
    final AccProjectDeliverableMapper roots = mock(AccProjectDeliverableMapper.class);
    final ProjectDeliverableSubmissionMapper submissions = mock(ProjectDeliverableSubmissionMapper.class);
    final ProjectDeliverableSourceVersionMapper sources = mock(ProjectDeliverableSourceVersionMapper.class);
    final ProjectDeliverableSourceAttachmentMapper attachments = mock(ProjectDeliverableSourceAttachmentMapper.class);
    final ProjectDeliverableRuleApi rules = mock(ProjectDeliverableRuleApi.class);
    final ProjectDeliverableAccess access = mock(ProjectDeliverableAccess.class);
    final FileArtifactApi files = mock(FileArtifactApi.class);
    final FileEvidenceApi facts = mock(FileEvidenceApi.class);
    final ProjectBusinessResultEvidenceApi results = mock(ProjectBusinessResultEvidenceApi.class);
    final PlatformBusinessEventApi outbox = mock(PlatformBusinessEventApi.class);
    final ProjectDeliverableOwnerSources ownerSources = mock(ProjectDeliverableOwnerSources.class);
    final ProjectDocumentSourceRegistry documentSources = mock(ProjectDocumentSourceRegistry.class);
    final ProjectDeliverableSubmissionService service = new ProjectDeliverableSubmissionService(roots, submissions, sources,
            attachments, rules, access, files, facts, results, outbox, ownerSources, documentSources);
    final AccProjectDeliverableDO root = new AccProjectDeliverableDO();
    final Map<String, ProjectDeliverableSubmissionDO> saved = new HashMap<>();
    ProjectDeliverableSourceVersionDO current;
    ProjectDeliverableRuleApi.Context context;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        root.setId(31L); root.setTenantId(7L); root.setProjectId(9L); root.setDeliverableCode("D1");
        root.setName("施工材料"); root.setRequired(true); root.setStatus("PENDING"); root.setVersion(0);
        context = new ProjectDeliverableRuleApi.Context(9L, 15L, "ACTIVE", 11L, "S1", "T1",
                JsonUtils.parseTree("""
                {"minimumQuantity":1,"allowedSources":["UPLOAD","BUSINESS_RESULT"],"confirmationRule":{"predicate":"TASK","parameters":{"refCode":"T1"}}}
                """));
        when(roots.selectById(31L)).thenReturn(root); when(roots.selectByIdForUpdate(any())).thenReturn(root);
        when(roots.updateById(any(AccProjectDeliverableDO.class))).thenReturn(1);
        when(rules.lock(9L, "D1")).thenReturn(context); when(rules.read(9L, "D1")).thenReturn(context);
        when(access.require(any(), anyBoolean(), anyBoolean())).thenReturn(11L);
        when(rules.evaluate(9L, "D1")).thenReturn(new ProjectDeliverableRuleApi.Decision(true, "DELIVERABLE_RULE_SATISFIED", "{}"));
        var file = new FileArtifactVersionFact(40L, 1, "slot1", "PROJECT_DELIVERABLE_DOCUMENT", "proof.pdf", 100L,
                "application/pdf", "a".repeat(64), "AVAILABLE", "ACTIVE", new FileFactVersion(1, 1, 1), 1L);
        when(files.inspect(any())).thenReturn(file); when(files.lockAndRevalidate(any())).thenReturn(file);
        when(facts.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true, "FILE_EVIDENCE_VALID", 1, 1, 1));
        when(attachments.insert(any(ProjectDeliverableSourceAttachmentDO.class))).thenReturn(1);
        when(sources.selectCurrentForUpdate(any())).thenAnswer(i -> current);
        when(sources.insert(any(ProjectDeliverableSourceVersionDO.class))).thenAnswer(i -> { current = i.getArgument(0); return 1; });
        when(sources.updateById(any(ProjectDeliverableSourceVersionDO.class))).thenReturn(1);
        when(submissions.selectRequest(anyLong(), anyLong(), anyString())).thenAnswer(i -> saved.get(i.getArgument(2)));
        when(submissions.insert(any(ProjectDeliverableSubmissionDO.class))).thenAnswer(i -> {
            ProjectDeliverableSubmissionDO row = i.getArgument(0); saved.put(row.getRequestKey(), row); return 1;
        });
        when(submissions.updateById(any(ProjectDeliverableSubmissionDO.class))).thenReturn(1);
        when(submissions.selectSource(anyLong(), anyLong())).thenAnswer(i -> saved.values().stream()
                .filter(row -> row.getSourceVersionId().equals(i.getArgument(1))).findFirst().orElse(null));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    private FileEvidenceApi.Document businessDocument(boolean available) {
        return new FileEvidenceApi.Document(71L, "SOL", "REQUIREMENT_ANALYSIS_REVISION", "81",
                "FORM_FIELD_ATTACHMENT/report", "slot2", 41L, 1, "b".repeat(64), "requirement.pdf", available);
    }
    private void configureCollection(String status, String source) {
        context = new ProjectDeliverableRuleApi.Context(9L, 15L, status, null, "S1", "T1",
                JsonUtils.parseTree("{\"minimumQuantity\":1,\"allowedSources\":[\"UPLOAD\"],\"automaticSources\":[\"" + source
                        + "\"],\"confirmationRule\":{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}}"));
        when(rules.read(9L, "D1")).thenReturn(context); when(rules.lock(9L, "D1")).thenReturn(context);
        when(documentSources.resolve(eq(7L), any())).thenReturn(new FileDocumentSourceProvider.Scope(9L, "SOL.REQUIREMENT_DOCUMENT"));
    }
    @Test void businessDocumentCollectsWithoutManualSubmissionAndDetachInvalidatesWithoutRewritingHistory() {
        configureCollection("ACTIVE", "SOL.REQUIREMENT_DOCUMENT");
        var scope = new FileDocumentSourceProvider.Scope(9L, "SOL.REQUIREMENT_DOCUMENT");
        service.collectDocument(root, scope, businessDocument(true), "attached-1");
        assertEquals("ACCEPTED", root.getStatus());
        assertEquals(1, saved.size());
        var original = saved.get("file-event:attached-1");
        String originalEvidence = original.getSourceEvidence(), originalDecision = original.getDecisionEvidence();
        service.collectDocument(root, scope, businessDocument(true), "attached-1");
        assertEquals(1, saved.size());
        service.collectDocument(root, scope, businessDocument(false), "detached-1");
        assertEquals("PENDING", root.getStatus());
        assertEquals(2, saved.size());
        assertEquals(originalEvidence, original.getSourceEvidence());
        assertEquals(originalDecision, original.getDecisionEvidence());
        verifyNoInteractions(access, files, results);
    }
    @Test void unconfiguredClosedOrOtherProjectEventsCannotCreateSubmissions() {
        var scope = new FileDocumentSourceProvider.Scope(9L, "SOL.REQUIREMENT_DOCUMENT");
        configureCollection("ACTIVE", "ACC.FINAL_REPORT");
        service.collectDocument(root, scope, businessDocument(true), "unconfigured");
        configureCollection("NORMAL_CLOSED", "SOL.REQUIREMENT_DOCUMENT");
        service.collectDocument(root, scope, businessDocument(true), "closed");
        configureCollection("ACTIVE", "SOL.REQUIREMENT_DOCUMENT");
        service.collectDocument(root, new FileDocumentSourceProvider.Scope(10L, scope.sourceCode()), businessDocument(true), "other-project");
        assertTrue(saved.isEmpty());
        verifyNoInteractions(outbox);
    }
    @Test void aBusinessEventWithNoAvailableDocumentCannotSatisfyTheDeliverable() {
        configureCollection("ACTIVE", "SOL.REQUIREMENT_DOCUMENT");
        service.collectDocument(root, new FileDocumentSourceProvider.Scope(9L, "SOL.REQUIREMENT_DOCUMENT"), businessDocument(false), "not-available");
        assertEquals("PENDING", root.getStatus());
        assertTrue(saved.isEmpty());
    }

    private ProjectDeliverableSubmissionService.Submission upload(int version) {
        return new ProjectDeliverableSubmissionService.Submission(15L, version, "UPLOAD",
                List.of(new ProjectDeliverableSubmissionService.FileSelection(40L, 1, "slot1")), null);
    }
    @Test void outboxFailureRollsBackAllSubmissionWritesAndTheSupersededSource() {
        // Real Spring transaction interception and an isolated JDBC ledger. Production MySQL/Mapper SQL is
        // exercised separately by migration/browser acceptance; this test proves the service transaction boundary.
        var database = new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(database);
            jdbc.execute("CREATE TABLE fixture_writes(kind VARCHAR(32),id BIGINT,payload CLOB,PRIMARY KEY(kind,id))");
            jdbc.update("INSERT INTO fixture_writes VALUES('ROOT',31,'PENDING'),('SOURCE',88,'CURRENT')");
            current = new ProjectDeliverableSourceVersionDO(); current.setId(88L); current.setSourceObjectType("ProjectDeliverableSubmission");
            current.setRelationStatus("CURRENT"); root.setCurrentSourceVersionId(88L);
            doAnswer(call -> {
                var row = call.<ProjectDeliverableSourceVersionDO>getArgument(0);
                return jdbc.update("UPDATE fixture_writes SET payload=? WHERE kind='SOURCE' AND id=?", row.getRelationStatus(), row.getId());
            }).when(sources).updateById(any(ProjectDeliverableSourceVersionDO.class));
            doAnswer(call -> {
                var row = call.<ProjectDeliverableSourceVersionDO>getArgument(0);
                return jdbc.update("INSERT INTO fixture_writes VALUES('SOURCE',?,?)", row.getId(), row.getRelationStatus());
            }).when(sources).insert(any(ProjectDeliverableSourceVersionDO.class));
            doAnswer(call -> {
                var row = call.<ProjectDeliverableSourceAttachmentDO>getArgument(0);
                return jdbc.update("INSERT INTO fixture_writes VALUES('ATTACHMENT',?,?)", row.getId(), row.getReferenceKey());
            }).when(attachments).insert(any(ProjectDeliverableSourceAttachmentDO.class));
            doAnswer(call -> {
                var row = call.<AccProjectDeliverableDO>getArgument(0);
                return jdbc.update("UPDATE fixture_writes SET payload=? WHERE kind='ROOT' AND id=?", row.getStatus(), row.getId());
            }).when(roots).updateById(any(AccProjectDeliverableDO.class));
            doAnswer(call -> {
                var row = call.<ProjectDeliverableSubmissionDO>getArgument(0);
                return jdbc.update("INSERT INTO fixture_writes VALUES('SUBMISSION',?,?)", row.getId(), row.getSourceEvidence());
            }).when(submissions).insert(any(ProjectDeliverableSubmissionDO.class));
            doAnswer(call -> {
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fixture_writes WHERE kind='SOURCE'", Integer.class));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fixture_writes WHERE kind='SUBMISSION'", Integer.class));
                assertEquals("ACCEPTED", jdbc.queryForObject("SELECT payload FROM fixture_writes WHERE kind='ROOT'", String.class));
                throw new IllegalStateException("OUTBOX_UNAVAILABLE");
            }).when(outbox).append(anyString(), anyString(), any());
            var factory = new org.springframework.aop.framework.ProxyFactory(service); factory.setProxyTargetClass(true);
            factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(database),
                    new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            var transactional = (ProjectDeliverableSubmissionService) factory.getProxy();
            var failure = assertThrows(IllegalStateException.class, () -> transactional.submit(9L, 31L, "request1", upload(0)));
            assertEquals("OUTBOX_UNAVAILABLE", failure.getMessage());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fixture_writes", Integer.class));
            assertEquals("PENDING", jdbc.queryForObject("SELECT payload FROM fixture_writes WHERE kind='ROOT'", String.class));
            assertEquals("CURRENT", jdbc.queryForObject("SELECT payload FROM fixture_writes WHERE kind='SOURCE'", String.class));
        } finally { database.shutdown(); }
    }
    @Test void validFileAndConfiguredConditionAutomaticallySatisfyAndReplayOneIntent() {
        var submitted = service.submit(9L, 31L, "request1", upload(0));
        assertEquals("ACCEPTED", submitted.status()); assertEquals(1, root.getVersion());
        assertEquals(submitted, service.submit(9L, 31L, "request1", upload(0)));
        verify(sources, times(1)).insert(any(ProjectDeliverableSourceVersionDO.class));
        verify(outbox, times(1)).append(anyString(), anyString(), any());
        assertTrue(service.revalidate(root).satisfied());
    }
    @Test void submittedFileWaitsForBusinessConditionThenGateRevalidatesAutomatically() {
        when(rules.evaluate(9L, "D1")).thenReturn(new ProjectDeliverableRuleApi.Decision(false, "DELIVERABLE_RULE_NOT_SATISFIED", "{}"));
        assertEquals("PENDING", service.submit(9L, 31L, "request1", upload(0)).status());
        String original = saved.get("request1").getDecisionEvidence();
        when(rules.evaluate(9L, "D1")).thenReturn(new ProjectDeliverableRuleApi.Decision(true, "DELIVERABLE_RULE_SATISFIED", "{}"));
        assertTrue(service.revalidate(root).satisfied()); assertEquals("ACCEPTED", root.getStatus());
        assertEquals(original, saved.get("request1").getDecisionEvidence());
    }
    @Test void invalidatedFileBlocksGateWithoutRewritingSubmissionHistory() {
        service.submit(9L, 31L, "request1", upload(0));
        String evidence = saved.get("request1").getSourceEvidence();
        when(facts.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false, "FILE_EVIDENCE_UNAVAILABLE", 2, 2, 1));
        assertFalse(service.revalidate(root).satisfied()); assertEquals("PENDING", root.getStatus());
        assertEquals(evidence, saved.get("request1").getSourceEvidence());
    }
    @Test void reevaluationEventsMeetPlatformIdentityContractWithoutDuplicatingUnchangedStatus() {
        when(rules.evaluate(9L, "D1")).thenReturn(new ProjectDeliverableRuleApi.Decision(false, "DELIVERABLE_RULE_NOT_SATISFIED", "{}"));
        service.submit(9L, 31L, "request1", upload(0));
        String original = saved.get("request1").getDecisionEvidence();
        var events = new ArrayList<cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent>();
        doAnswer(call -> {
            var event = call.<cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent>getArgument(2);
            // The platform Outbox rejects a missing or different eventId in the payload.
            assertEquals(event.eventId(), JsonUtils.parseTree(event.eventPayload()).path("eventId").asText());
            if ("ProjectDeliverableEvaluated.v1".equals(event.eventType())) events.add(event);
            return null;
        }).when(outbox).append(anyString(), anyString(), any());
        when(rules.evaluate(9L, "D1")).thenReturn(new ProjectDeliverableRuleApi.Decision(true, "DELIVERABLE_RULE_SATISFIED", "{}"));
        assertTrue(service.refresh(9L, 31L).satisfied());
        assertTrue(service.refresh(9L, 31L).satisfied());
        assertEquals(1, events.size());
        when(facts.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false, "FILE_EVIDENCE_UNAVAILABLE", 2, 2, 1));
        assertFalse(service.refresh(9L, 31L).satisfied());
        assertEquals(2, events.size());
        assertNotEquals(events.getFirst().eventId(), events.getLast().eventId());
        assertEquals("ACCEPTED", JsonUtils.parseTree(events.getFirst().eventPayload()).path("status").asText());
        assertEquals("PENDING", JsonUtils.parseTree(events.getLast().eventPayload()).path("status").asText());
        assertEquals(original, saved.get("request1").getDecisionEvidence());
    }
    @Test void sameKeyDifferentIntentFailsBeforeChangingSource() {
        service.submit(9L, 31L, "request1", upload(0));
        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "request1", upload(1)));
        verify(sources, times(1)).insert(any(ProjectDeliverableSourceVersionDO.class));
    }
    @Test void staleVersionAndMissingMaterialsCannotSubmit() {
        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "stale", upload(1)));
        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "missing",
                new ProjectDeliverableSubmissionService.Submission(15L, 0, "UPLOAD", List.of(), null)));
        verify(sources, never()).insert(any(ProjectDeliverableSourceVersionDO.class));
    }
    @Test void replacementRetainsOriginalEvidenceAndOriginalRetryReceipt() {
        var old = service.submit(9L, 31L, "request1", upload(0));
        var previous = current;
        var next = service.submit(9L, 31L, "request2", upload(1));
        assertNotEquals(old.sourceVersionId(), next.sourceVersionId());
        assertEquals("SUPERSEDED", previous.getRelationStatus()); assertEquals(2, saved.size());
        assertEquals(old, service.submit(9L, 31L, "request1", upload(0)));
        assertEquals(next.sourceVersionId(), root.getCurrentSourceVersionId());
    }
    @Test void tenantProjectAndPermissionFailuresNeverSaveMaterial() {
        assertThrows(RuntimeException.class, () -> service.submit(10L, 31L, "wrong-project", upload(0)));
        TenantContextHolder.setTenantId(8L);
        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "wrong-tenant", upload(0)));
        TenantContextHolder.setTenantId(7L);
        when(access.require(context, true, true)).thenThrow(new IllegalArgumentException("permission denied"));
        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "no-access", upload(0)));
        verify(sources, never()).insert(any(ProjectDeliverableSourceVersionDO.class));
    }
    @Test void validBusinessResultIsRecheckedByExactIdentityAndRevocationBlocksIt() {
        var type = new BusinessResultSource.Type("SOL", "REQUIREMENT_ANALYSIS", "REQUIREMENT_ANALYSIS_COMPLETED");
        var result = new BusinessResultSource.Result(7L, 9L, type, "55", "56", "1", "1", BusinessResultSource.Validity.CURRENT,
                java.time.LocalDateTime.parse("2026-09-20T12:00:00"));
        when(results.types()).thenReturn(List.of(new BusinessResultSource.Descriptor(type, true, true, true)));
        when(results.lockAndInspect(any())).thenReturn(BusinessResultSource.Observation.available(result));
        var query = new BusinessResultSource.Query(7L, 9L, type, "55", "56");
        assertEquals("ACCEPTED", service.submit(9L, 31L, "business", new ProjectDeliverableSubmissionService.Submission(
                15L, 0, "BUSINESS_RESULT", List.of(), query)).status());
        when(results.lockAndInspect(any())).thenReturn(BusinessResultSource.Observation.available(new BusinessResultSource.Result(
                7L, 9L, type, "55", "56", "1", "2", BusinessResultSource.Validity.REVOKED, result.formedAt())));
        assertFalse(service.revalidate(root).satisfied());
        assertEquals("PENDING", root.getStatus());
    }
    @Test void automaticBusinessProjectionIsValidatedWithoutReplacingItsHistory() {
        current = new ProjectDeliverableSourceVersionDO(); current.setId(88L); current.setSourceObjectType("AcceptanceReportVersion");
        root.setCurrentSourceVersionId(88L);
        when(ownerSources.revalidate(root, current)).thenReturn(new ProjectDeliverableOwnerSources.Evidence(true, "VALID", 1, List.of()));
        assertTrue(service.revalidate(root).satisfied());
        assertEquals(88L, root.getCurrentSourceVersionId());
        when(ownerSources.revalidate(root, current)).thenReturn(new ProjectDeliverableOwnerSources.Evidence(false, "FILE_EVIDENCE_UNAVAILABLE", 0, List.of()));
        assertFalse(service.revalidate(root).satisfied());
        verify(sources, never()).updateById(any(ProjectDeliverableSourceVersionDO.class));
        verify(submissions, never()).insert(any(ProjectDeliverableSubmissionDO.class));
    }
    @Test void manualSubmissionCannotTakeOverAnAutomaticBusinessTarget() {
        when(ownerSources.ownerType(root)).thenReturn("ACCEPTANCE_REPORT");
        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "manual", upload(0)));
        verify(sources, never()).insert(any(ProjectDeliverableSourceVersionDO.class));
        verify(files, never()).lockAndRevalidate(any());
    }
    @Test void oldAcceptedRootWithoutAnySourceDoesNotPass() {
        root.setStatus("ACCEPTED");
        assertFalse(service.revalidate(root).satisfied());
    }
}
