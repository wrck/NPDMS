package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.*;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptance.NativeAcceptanceDeliveryAccess;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CompletionSatisfactionBundleServiceTest {
    @Mock CompletionSatisfactionBundleMapper bundles;
    @Mock CompletionSatisfactionSigningMapper signings;
    @Mock CompletionCertificateMapper certificates;
    @Mock CompletionCertificateDeviceMapper devices;
    @Mock NativeAcceptanceDeliveryAccess access;
    @Mock PlatformDeliveryMaterialApi deliveryMaterials;
    @Mock cn.iocoder.yudao.module.pms.platform.api.file.BusinessGrantGeneratedFileApi generatedDocuments;
    @Mock SatisfactionQuestionnaireMapper documentQuestionnaires;
    @Mock SatisfactionResponseMapper documentResponses;
    @InjectMocks CompletionSatisfactionBundleService service;

    @Test void freezesExplicitCertificateWithoutPrivateContentOrUrls() {
        var certificate = certificate();
        certificate.setContent("private content"); certificate.setAttachmentUrl("private-url");
        certificate.setEngineerName("工程师");
        when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate);
        when(devices.selectListByCertificateId(20L)).thenReturn(List.of());
        when(bundles.insert(any(CompletionSatisfactionBundleDO.class))).thenReturn(1);
        service.bind(7L, 30L, task(), 20L);
        var captured = ArgumentCaptor.forClass(CompletionSatisfactionBundleDO.class);
        verify(bundles).insert(captured.capture());
        assertEquals(20L, captured.getValue().getCertificateId());
        assertEquals("SAT-10", captured.getValue().getCollectionKey());
        assertEquals(3L, captured.getValue().getCertificateVersion());
        assertFalse(captured.getValue().getCertificateSnapshot().contains("private"));
        assertEquals("工程师", JsonUtils.parseObject(captured.getValue().getCertificateSnapshot(),
                CompletionSatisfactionBundleService.CertificateSnapshot.class).engineerName());
    }

    @Test void rejectsAnotherProjectBeforeBinding() {
        var certificate = certificate(); certificate.setProjectId(99L);
        when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate);
        assertThrows(IllegalStateException.class, () -> service.bind(7L, 30L, task(), 20L));
        verify(bundles, never()).insert(any(CompletionSatisfactionBundleDO.class));
    }

    @Test void rejectsAlreadyConfirmedCertificateWithoutCreatingNewPair() {
        var certificate = certificate(); certificate.setStatus(2);
        when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate);
        assertThrows(IllegalStateException.class, () -> service.bind(7L, 30L, task(), 20L));
        verify(bundles, never()).insert(any(CompletionSatisfactionBundleDO.class));
    }

    @Test void repeatedPushReusesPairAndCannotSwitchCertificates() {
        when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate());
        when(bundles.selectByCollectionForUpdate(any())).thenReturn(bundle());
        assertEquals(40L, service.bind(7L, 30L, task(), 20L));
        assertThrows(IllegalStateException.class, () -> service.bind(7L, 30L, task(), 21L));
        verify(bundles, never()).insert(any(CompletionSatisfactionBundleDO.class));
    }

    @Test void refusesUnconfirmedJointSubmissionBeforeAnyWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.confirm(7L, grant(), task(), 50L, false, signature()));
        verifyNoInteractions(bundles, signings, certificates, deliveryMaterials);
    }

    @Test void bindsOneCanonicalSignatureToBothDocumentsAndConfirmsOnce() {
        arrange();
        when(bundles.confirmPendingCertificate(any())).thenReturn(1);
        when(signings.insert(any(CompletionSatisfactionSigningDO.class))).thenReturn(1);
        service.confirm(7L, grant(), task(), 50L, true, signature());
        var signing = ArgumentCaptor.forClass(CompletionSatisfactionSigningDO.class);
        verify(signings).insert(signing.capture());
        assertEquals(11L, signing.getValue().getQuestionnaireId());
        assertEquals(50L, signing.getValue().getResponseId());
        assertEquals(40L, signing.getValue().getBundleId());
        assertEquals(signature(), JsonUtils.parseObject(signing.getValue().getSignatureFileFact(),
                SatisfactionResponseSubmissionService.FileFact.class));
        verify(bundles).confirmPendingCertificate(argThat(query -> query.expectedVersion().equals(3L)));
        verify(deliveryMaterials).registerBusinessResultMaterial(eq("ACC"), eq("completionCertificate"), eq(20L),
                eq("COMPLETION_CERTIFICATE"), eq("completionCertificate"), eq("20"), isNull(), any(), eq(9L));
    }

    @Test void versionConflictProducesNoSigningOrCompletionMaterial() {
        arrange();
        assertThrows(IllegalStateException.class, () -> service.confirm(7L, grant(), task(), 50L, true, signature()));
        verify(signings, never()).insert(any(CompletionSatisfactionSigningDO.class));
        verifyNoInteractions(deliveryMaterials);
    }

    @Test void remediationAppendsSigningWithoutChangingOriginalConfirmation() {
        arrange();
        var certificate = certificate(); certificate.setStatus(2); certificate.setVersion(4L);
        when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate);
        when(signings.selectFirstSigning(any())).thenReturn(new CompletionSatisfactionSigningDO());
        when(signings.insert(any(CompletionSatisfactionSigningDO.class))).thenReturn(1);
        service.confirm(7L, grant(), task(), 51L, true, signature());
        verify(bundles, never()).confirmPendingCertificate(any());
        verifyNoInteractions(deliveryMaterials);
    }

    @Test void rejectsCrossTenantBundleAndRevokedIssuerRights() {
        var other = bundle(); other.setTenantId(8L);
        when(bundles.selectById(40L)).thenReturn(other);
        assertThrows(IllegalStateException.class, () -> service.confirm(7L, grant(), task(), 50L, true, signature()));
        when(bundles.selectById(40L)).thenReturn(bundle());
        when(access.requireCommand(eq(7L), eq(30L), anyString(), eq(9L), eq("audit")))
                .thenThrow(new IllegalStateException("permission revoked"));
        assertThrows(IllegalStateException.class, () -> service.confirm(7L, grant(), task(), 50L, true, signature()));
        verify(bundles, never()).confirmPendingCertificate(any());
        verify(signings, never()).insert(any(CompletionSatisfactionSigningDO.class));
    }

    @Test void completionMaterialFailureRollsBackResponseCertificateAndSigningTogether() {
        var db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(db);
            jdbc.execute("CREATE TABLE proof(id BIGINT PRIMARY KEY, status INT, version BIGINT)");
            jdbc.execute("CREATE TABLE response(id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE signing(response_id BIGINT PRIMARY KEY)");
            jdbc.update("INSERT INTO proof VALUES(20,1,3)");
            arrange();
            when(bundles.confirmPendingCertificate(any())).thenAnswer(invocation -> jdbc.update(
                    "UPDATE proof SET status=2,version=4 WHERE id=20 AND status=1 AND version=3"));
            when(signings.insert(any(CompletionSatisfactionSigningDO.class))).thenAnswer(invocation -> jdbc.update(
                    "INSERT INTO signing VALUES(?)", ((CompletionSatisfactionSigningDO) invocation.getArgument(0)).getResponseId()));
            doThrow(new IllegalStateException("material registration failed")).when(deliveryMaterials)
                    .registerBusinessResultMaterial(anyString(), anyString(), anyLong(), anyString(), anyString(),
                            anyString(), isNull(), any(), anyLong());
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(db));
            assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
                jdbc.update("INSERT INTO response VALUES(50)");
                service.confirm(7L, grant(), task(), 50L, true, signature());
                return null;
            }));
            assertEquals(1, jdbc.queryForObject("SELECT status FROM proof WHERE id=20", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT version FROM proof WHERE id=20", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM response", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM signing", Integer.class));
        } finally { db.shutdown(); }
    }

    @Test void previewImageMustMatchCanonicalUploadedSignatureBeforeAnyWrites() {
        assertThrows(IllegalArgumentException.class, () -> service.confirm(7L,grant(),task(),50L,true,signature(),"data:image/png;base64,aW52YWxpZA=="));
        verifyNoInteractions(bundles,signings,certificates,generatedDocuments);
    }

    @Test void secondDocumentFailureRollsBackBothGeneratedFilesAndTheJointSubmission() {
        var db=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc=new JdbcTemplate(db);
            jdbc.execute("CREATE TABLE proof(id BIGINT PRIMARY KEY,status INT,version BIGINT)");jdbc.update("INSERT INTO proof VALUES(20,1,3)");
            jdbc.execute("CREATE TABLE response(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE signing(response_id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE document(kind VARCHAR(30))");
            arrange();var proof=certificate();proof.setSatisfactionTemplateId(90L);
            when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(proof);
            var bundle=bundle();bundle.setCertificateSnapshot(JsonUtils.toJsonString(new CompletionSatisfactionBundleService.CertificateSnapshot(
                    20L,3L,9L,"proof-20","完工证明",null,null,null,null,null,null,null,null,null,null,null,null,List.of())));
            when(bundles.selectById(40L)).thenReturn(bundle);
            when(bundles.confirmPendingCertificate(any())).thenAnswer(invocation->jdbc.update("UPDATE proof SET status=2,version=4 WHERE id=20 AND status=1 AND version=3"));
            when(signings.insert(any(CompletionSatisfactionSigningDO.class))).thenAnswer(invocation->jdbc.update("INSERT INTO signing VALUES(50)"));
            var questionnaire=new SatisfactionQuestionnaireDO();questionnaire.setTenantId(7L);questionnaire.setFrozenQuestionJson("{\"questions\":[]}");
            var response=new SatisfactionResponseDO();response.setTenantId(7L);response.setAnswerSnapshot("{\"answers\":[]}");
            when(documentQuestionnaires.selectById(11L)).thenReturn(questionnaire);when(documentResponses.selectById(50L)).thenReturn(response);
            when(generatedDocuments.create(any())).thenAnswer(invocation->{
                var command=(cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantGeneratedFileCommand)invocation.getArgument(0);
                jdbc.update("INSERT INTO document VALUES(?)",command.purposeCode());
                if(command.purposeCode().startsWith("SATISFACTION"))throw new IllegalStateException("second document failed");return null;
            });
            var grant=grant();grant.setGrantVersion(1);
            var transaction=new TransactionTemplate(new DataSourceTransactionManager(db));
            var canonical=new SatisfactionResponseSubmissionService.FileFact("SIGNATURE","slot-1",1,100L,1,"signature-ref",1,1,1,3L,cn.hutool.crypto.digest.DigestUtil.sha256Hex("image"));
            assertThrows(IllegalStateException.class,()->transaction.execute(status->{jdbc.update("INSERT INTO response VALUES(50)");service.confirm(7L,grant,task(),50L,true,canonical,"data:image/png;base64,aW1hZ2U=");return null;}));
            assertEquals(1,jdbc.queryForObject("SELECT status FROM proof",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM response",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM signing",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM document",Integer.class));
        }finally{db.shutdown();}
    }

    private void arrange() {
        when(bundles.selectById(40L)).thenReturn(bundle());
        when(certificates.selectDeliveryOwnerForUpdate(any())).thenReturn(certificate());
    }
    private CompletionCertificateDO certificate() {
        var row = new CompletionCertificateDO(); row.setId(20L); row.setTenantId(7L); row.setProjectId(9L);
        row.setName("本次完工证明"); row.setStatus(1); row.setVersion(3L); return row;
    }
    private CompletionSatisfactionBundleDO bundle() {
        var row = new CompletionSatisfactionBundleDO(); row.setId(40L); row.setTenantId(7L); row.setProjectId(9L);
        row.setCertificateId(20L); row.setCertificateVersion(3L); row.setCollectionKey("SAT-10"); return row;
    }
    private SatisfactionCollectionTaskDO task() {
        var row = new SatisfactionCollectionTaskDO(); row.setId(10L); row.setTenantId(7L); row.setProjectId(9L);
        row.setCollectionKey("SAT-10"); row.setQuestionnaireId(11L); return row;
    }
    private SatisfactionAccessGrantDO grant() {
        var row = new SatisfactionAccessGrantDO(); row.setId(60L); row.setTenantId(7L);
        row.setCompletionBundleId(40L); row.setQuestionnaireId(11L); row.setCreator("30"); return row;
    }
    private SatisfactionResponseSubmissionService.FileFact signature() {
        return new SatisfactionResponseSubmissionService.FileFact("SIGNATURE", "slot-1", 1, 100L, 1,
                "signature-ref", 1, 1, 1, 3L, "a".repeat(64));
    }
}
