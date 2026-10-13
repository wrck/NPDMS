package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTemplateFact;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.platform.api.file.NativeGeneratedFileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class CompletionConfirmationWorkflowServiceTest {
    @Mock CompletionCertificateService legacy;
    @Mock CompletionCertificateMapper certificates;
    @Mock cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionPdfBindingMapper pdfBindings;
    @Mock CompletionSatisfactionBundleMapper bundles;
    @Mock SatisfactionCollectionTaskMapper tasks;
    @Mock SatisfactionQuestionnaireMapper questionnaires;
    @Mock SatisfactionResponseMapper responses;
    @Mock CompletionSatisfactionSigningMapper signings;
    @Mock SatisfactionResultMapper results;
    @Mock IndependentSatisfactionService independent;
    @Mock SatisfactionQuestionnaireTemplateApi templates;
    @Mock SatisfactionAccessGrantService grants;
    @Mock CompletionConfirmationPrintService printLayouts;
    @Mock NativeAcceptanceDeliveryAccess access;
    @Mock ProjectAcceptanceContextApi projects;
    @Mock ProjectScopeApi scopes;
    @Mock PermissionApi permissions;
    @Mock NativeGeneratedFileApi files;
    @Mock cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi deliveryMaterials;
    @Mock cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi fileEvidence;
    @Mock SatisfactionQuestionnaireTemplateMapper templateMapper;
    @InjectMocks CompletionConfirmationWorkflowService service;
    CompletionCertificateDO certificate;
    @BeforeEach void setup(){
        lenient().when(pdfBindings.insert(any(cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionPdfBindingDO.class))).thenReturn(1);
        certificate=new CompletionCertificateDO();certificate.setId(10L);certificate.setTenantId(1L);
        certificate.setProjectId(9L);certificate.setVersion(3L);certificate.setStatus(0);certificate.setSatisfactionTemplateId(11L);
        lenient().when(certificates.selectById(10L)).thenReturn(certificate);
        lenient().when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate);
        var context=new ProjectAcceptanceContextApi.Context(9L,9L,5L,1L,"ACTIVE");
        lenient().when(projects.inspect(any())).thenReturn(context);lenient().when(projects.lock(any(),any(),any())).thenReturn(context);
        lenient().when(permissions.hasAnyPermissions(eq(1L),any(String[].class))).thenReturn(true);
    }
    private void published(boolean uploads){
        var template=new SatisfactionQuestionnaireTemplateDO();template.setId(11L);template.setTenantId(1L);template.setStatus("PUBLISHED");template.setCurrentRevisionId(12L);
        when(templateMapper.selectById(11L)).thenReturn(template);
        when(templates.inspectPublished(11L,12L)).thenReturn(new SatisfactionTemplateFact("FOUND",11L,12L,1,"rule",java.math.BigDecimal.valueOf(80)));
        if(uploads)when(independent.create(eq(1L),eq(1L),any(),eq("completion-certificate:10"))).thenReturn(new IndependentSatisfactionService.Created(20L,21L,"SAT-20"));
        if(uploads)when(grants.create(eq(1L),eq(1L),eq(20L),any(),eq(10L))).thenReturn(new SatisfactionAccessGrantService.CreatedGrant(30L,1,"temporary",LocalDateTime.now().plusDays(7)));
    }
    @Test void firstIssueCreatesExactlyOneFrozenSurveyAndJointGrant(){
        published(true);var issued=service.issue(1L,1L,10L);
        assertEquals(20L,issued.taskId());assertEquals("/satisfaction-questionnaires/temporary",issued.signPath());
        var order=inOrder(independent,legacy,grants);order.verify(independent).create(eq(1L),eq(1L),any(),eq("completion-certificate:10"));
        order.verify(legacy).submitCompletionCertificate(10L);order.verify(grants).create(eq(1L),eq(1L),eq(20L),any(),eq(10L));
    }
    @Test void unchangedCompletionFormAutomaticallyUsesThePublishedOriginalForm(){
        published(true);certificate.setSatisfactionTemplateId(null);
        var selected=new SatisfactionQuestionnaireTemplateDO();selected.setId(11L);
        when(templateMapper.selectCompletionPublished(any())).thenReturn(List.of(selected));
        when(certificates.updateById(any(CompletionCertificateDO.class))).thenReturn(1);
        service.issue(1L,1L,10L);
        verify(certificates).updateById(argThat((CompletionCertificateDO row)->Long.valueOf(11).equals(row.getSatisfactionTemplateId())));
        verify(independent).create(eq(1L),eq(1L),argThat(command->command.templateId().equals(11L)),eq("completion-certificate:10"));
    }
    @Test void multiplePublishedQuestionnairesNeverCauseAnArbitraryChoiceOrPartialIssue(){
        certificate.setSatisfactionTemplateId(null);
        when(templateMapper.selectCompletionPublished(any())).thenReturn(List.of(new SatisfactionQuestionnaireTemplateDO(),new SatisfactionQuestionnaireTemplateDO()));
        assertThrows(IllegalArgumentException.class,()->service.issue(1L,1L,10L));
        verifyNoInteractions(independent,grants);verify(certificates,never()).updateById(any(CompletionCertificateDO.class));
    }
    @Test void unavailableOriginalFormDoesNotFallBackToAnUnrelatedTemplate(){
        certificate.setSatisfactionTemplateId(null);
        when(templateMapper.selectCompletionPublished(any())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->service.issue(1L,1L,10L));
        verifyNoInteractions(independent,grants);verify(legacy,never()).submitCompletionCertificate(any());
    }
    @Test void reissueReusesPairAndRevokesPreviousLinks(){
        certificate.setStatus(1);var bundle=new CompletionSatisfactionBundleDO();bundle.setId(40L);bundle.setCollectionKey("SAT-20");
        when(bundles.selectByCertificate(any())).thenReturn(bundle);
        var task=new SatisfactionCollectionTaskDO();task.setId(20L);
        when(tasks.selectByCollection(any())).thenReturn(List.of(task));
        when(grants.create(eq(1L),eq(1L),eq(20L),any(),eq(10L))).thenReturn(new SatisfactionAccessGrantService.CreatedGrant(31L,2,"replacement",LocalDateTime.now().plusDays(7)));
        service.issue(1L,1L,10L);
        verifyNoInteractions(independent,templates);verify(legacy,never()).submitCompletionCertificate(any());
        var order=inOrder(bundles,grants);order.verify(bundles).selectByCertificate(any());order.verify(bundles).revokeActiveGrants(any());order.verify(grants).create(eq(1L),eq(1L),eq(20L),any(),eq(10L));
    }
    @Test void missingOrUnpublishedSelectionLeavesDraftAndNoSurvey(){
        assertThrows(IllegalArgumentException.class,()->service.issue(1L,1L,10L));
        verifyNoInteractions(independent,grants);verify(legacy,never()).submitCompletionCertificate(any());
    }
    @Test void confirmedRecordCannotBeReissuedOrVoided(){
        certificate.setStatus(2);
        assertThrows(IllegalStateException.class,()->service.issue(1L,1L,10L));
        assertThrows(IllegalStateException.class,()->service.voidRecord(1L,1L,10L));
        verifyNoInteractions(independent,grants);verify(certificates,never()).updateById(any(CompletionCertificateDO.class));
    }
    @Test void voidDraftOrIssuedRecordRevokesItsActiveLinks(){
        certificate.setStatus(1);var bundle=new CompletionSatisfactionBundleDO();bundle.setId(40L);
        when(bundles.selectByCertificate(any())).thenReturn(bundle);when(certificates.updateById(any(CompletionCertificateDO.class))).thenReturn(1);
        service.voidRecord(1L,1L,10L);
        verify(certificates).updateById(argThat((CompletionCertificateDO row)->row.getStatus()==4 && row.getVersion()==3L));verify(bundles).revokeActiveGrants(any());
    }
    @Test void onlyDraftCanBeDeleted(){
        certificate.setStatus(1);assertThrows(IllegalStateException.class,()->service.delete(1L,1L,10L));verify(legacy,never()).deleteCompletionCertificate(any());
    }
    @Test void unauthorizedOrInactiveProjectIssuesNothing(){
        doThrow(new IllegalStateException("denied")).when(access).requireCommand(any(),any(),any(),any(),any());
        assertThrows(IllegalStateException.class,()->service.issue(1L,1L,10L));verifyNoInteractions(independent,grants);
    }
    @Test void changedOwnerVersionOrNonPdfCannotRegisterDocument(){
        assertThrows(IllegalStateException.class,()->service.savePdf(1L,1L,10L,2L,"COMPLETION","key","%PDF-demo".getBytes()));
        assertThrows(IllegalArgumentException.class,()->service.savePdf(1L,1L,10L,3L,"COMPLETION","key","wrong".getBytes()));verifyNoInteractions(files);
    }
    @Test void newSurveyAndDraftStatusRollBackWhenGrantFails(){
        published(false);var dataSource=new org.h2.jdbcx.JdbcDataSource();dataSource.setURL("jdbc:h2:mem:completion_workflow_rollback;DB_CLOSE_DELAY=-1");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE proof(id BIGINT,status INT)");jdbc.execute("CREATE TABLE survey(id BIGINT)");jdbc.update("INSERT INTO proof VALUES(10,0)");
        when(independent.create(eq(1L),eq(1L),any(),any())).thenAnswer(call->{jdbc.update("INSERT INTO survey VALUES(20)");return new IndependentSatisfactionService.Created(20L,21L,"SAT-20");});
        doAnswer(call->{jdbc.update("UPDATE proof SET status=1 WHERE id=10");return null;}).when(legacy).submitCompletionCertificate(10L);
        when(grants.create(eq(1L),eq(1L),eq(20L),any(),eq(10L))).thenThrow(new IllegalStateException("grant failure"));
        var transaction=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        assertThrows(IllegalStateException.class,()->transaction.execute(status->service.issue(1L,1L,10L)));
        assertEquals(0,jdbc.queryForObject("SELECT status FROM proof WHERE id=10",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM survey",Integer.class));
    }
    private void existingPdf(String purpose, boolean available) {
        certificate.setCode("proof");
        byte[] content="%PDF-demo".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String digest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(content);
        var material=mock(cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.DeliveryMaterialView.class);
        when(deliveryMaterials.listByEntity("ACC","completionCertificate",10L)).thenReturn(List.of(material));
        when(material.status()).thenReturn("ACTIVE");when(material.sourceKind()).thenReturn("GENERATED");
        when(material.materialKind()).thenReturn("FILE");when(material.fileSha256()).thenReturn(digest);
        when(material.fileArtifactId()).thenReturn(100L);when(material.fileVersionNo()).thenReturn(1);
        lenient().when(material.id()).thenReturn(200L);
        lenient().when(material.fileName()).thenReturn("proof-COMPLETION-"+"a".repeat(32)+".pdf");
        when(fileEvidence.inspectDocumentByArtifact(1L,100L,1)).thenReturn(
            new cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Document(300L,"ACC","COMPLETION_CONFIRMATION","10",purpose,
                "reference",100L,1,digest,"proof.pdf",available));
    }
    @Test void samePdfReusesRegisteredFileAfterProjectLockForBothKinds() {
        existingPdf("COMPLETION_PDF/3",true);
        var file=service.savePdf(1L,1L,10L,3L,"COMPLETION","new-key","%PDF-demo".getBytes());
        assertEquals(200L,file.materialId());verifyNoInteractions(files);
        var order=inOrder(projects,deliveryMaterials);
        order.verify(projects).lock(any(),any(),any());
        order.verify(deliveryMaterials).listByEntity("ACC","completionCertificate",10L);
    }
    @Test void anotherKindOrVersionIsNotReused() {
        existingPdf("SATISFACTION_PDF/2",true);
        service.savePdf(1L,1L,10L,3L,"COMPLETION","new-key","%PDF-demo".getBytes());
        verify(files).create(any());
    }
    @Test void unavailablePdfIsNotReused() {
        existingPdf("SATISFACTION_PDF/3",false);
        service.savePdf(1L,1L,10L,3L,"SATISFACTION","new-key","%PDF-demo".getBytes());
        verify(files).create(any());
    }
    @Test void sameSatisfactionPdfAlsoReusesRegisteredFile() {
        existingPdf("SATISFACTION_PDF/3",true);
        assertEquals(200L,service.savePdf(1L,1L,10L,3L,"SATISFACTION","new-key","%PDF-demo".getBytes()).materialId());
        verifyNoInteractions(files);
    }

    @Test void findsRegisteredPdfBeforeAnyGeneration() {
        existingPdf("COMPLETION_PDF/3",true);
        var result=service.findRegisteredPdf(new CompletionConfirmationWorkflowService.PdfLookup(1L,1L,10L,3L,"COMPLETION","a".repeat(32)));
        assertEquals(200L,result.materialId());verifyNoInteractions(files);
    }
    @Test void differentTemplateIdentityDoesNotReuseExistingPdf() {
        certificate.setCode("proof");
        var material=mock(cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.DeliveryMaterialView.class);
        when(deliveryMaterials.listByEntity("ACC","completionCertificate",10L)).thenReturn(List.of(material));
        when(material.status()).thenReturn("ACTIVE");when(material.sourceKind()).thenReturn("GENERATED");
        when(material.materialKind()).thenReturn("FILE");when(material.fileName()).thenReturn("proof-COMPLETION-"+"a".repeat(32)+".pdf");
        assertNull(service.findRegisteredPdf(new CompletionConfirmationWorkflowService.PdfLookup(1L,1L,10L,3L,"COMPLETION","b".repeat(32))));
        verifyNoInteractions(files);
    }
    @Test void sameTemplateIdentityReusesPdfDespiteDifferentRasterBytes() {
        existingPdf("COMPLETION_PDF/3",true);
        byte[] content=("%PDF-raster-diff /ID [ <"+"a".repeat(32)+"> <"+"a".repeat(32)+"> ]").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(200L,service.savePdf(1L,1L,10L,3L,"COMPLETION","new-key",content).materialId());
        verifyNoInteractions(files);
    }

    @Test void lookupRejectsStaleOrVoidedRecordsBeforeFileQuery() {
        assertThrows(IllegalStateException.class,()->service.findRegisteredPdf(new CompletionConfirmationWorkflowService.PdfLookup(1L,1L,10L,2L,"COMPLETION","a".repeat(32))));
        certificate.setStatus(4);
        assertThrows(IllegalStateException.class,()->service.findRegisteredPdf(new CompletionConfirmationWorkflowService.PdfLookup(1L,1L,10L,3L,"COMPLETION","a".repeat(32))));
        verifyNoInteractions(deliveryMaterials,fileEvidence,files);
    }

    private cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.DeliveryMaterialView legacyPdf(Long id,Long artifact,LocalDateTime time,String digest) {
        return new cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.DeliveryMaterialView(
            id,"ACC","completionCertificate",10L,"COMPLETION_CERTIFICATE","FILE",null,9L,artifact,1,digest,
            "proof-COMPLETION.pdf",null,null,null,"proof","GENERATED","ACTIVE",null,"1",time);
    }
    @Test void latestEffectiveLegacyPdfBecomesBaselineWithoutGeneratingFile() {
        certificate.setCode("proof");
        var older=legacyPdf(20L,100L,LocalDateTime.of(2026,10,12,10,0),"a".repeat(64));
        var latest=legacyPdf(21L,101L,LocalDateTime.of(2026,10,13,10,0),"b".repeat(64));
        when(deliveryMaterials.listByEntity("ACC","completionCertificate",10L)).thenReturn(List.of(older,latest));
        when(fileEvidence.inspectDocumentByArtifact(1L,101L,1)).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Document(
            301L,"ACC","COMPLETION_CONFIRMATION","10","COMPLETION_PDF/3","ref",101L,1,"b".repeat(64),"proof-COMPLETION.pdf",true));
        var query=new CompletionConfirmationWorkflowService.PdfLookup(1L,1L,10L,3L,"COMPLETION","a".repeat(32));
        assertEquals(21L,service.findRegisteredPdf(query).materialId());
        var binding=ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionPdfBindingDO.class);
        verify(pdfBindings).insert(binding.capture());assertEquals(21L,binding.getValue().getMaterialId());
        when(pdfBindings.hasBaseline(any())).thenReturn(true);
        assertNull(service.findRegisteredPdf(new CompletionConfirmationWorkflowService.PdfLookup(1L,1L,10L,3L,"COMPLETION","b".repeat(32))));
        verifyNoInteractions(files);
    }
    @Test void invalidBaselineDoesNotAllowDuplicateGeneration() {
        var bound=new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionPdfBindingDO();bound.setMaterialId(21L);
        when(pdfBindings.selectBinding(any())).thenReturn(bound);
        var material=mock(cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi.DeliveryMaterialView.class);
        when(material.status()).thenReturn("WITHDRAWN");
        when(deliveryMaterials.listByEntity("ACC","completionCertificate",10L)).thenReturn(List.of(material));
        assertThrows(IllegalStateException.class,()->service.findRegisteredPdf(new CompletionConfirmationWorkflowService.PdfLookup(1L,1L,10L,3L,"COMPLETION","a".repeat(32))));
        verifyNoInteractions(files,fileEvidence);
    }

}
