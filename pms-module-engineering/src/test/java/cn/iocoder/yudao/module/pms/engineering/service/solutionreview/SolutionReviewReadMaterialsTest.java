package cn.iocoder.yudao.module.pms.engineering.service.solutionreview;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionSourceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionSourceMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolutionReviewReadMaterialsTest {
    @Mock SolutionTieredReviewService reviews;
    @Mock SolutionSourceMapper sources;
    @Mock RequirementAnalysisMapper requirements;
    @Mock PlatformDeliveryMaterialApi materials;
    @Mock FileEvidenceApi evidence;
    @Mock ObjectProvider<OwnerScopedFileContentApi> contents;
    @InjectMocks SolutionReviewReadMaterials service;
    private final String key = "SOL_REVIEW:1:42";
    private SolutionDO solution;
    private FileEvidenceApi.Document document;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        solution = new SolutionDO(); solution.setId(42L); solution.setTenantId(1L); solution.setProjectId(9L);
        solution.setScript("{\"references\":[{\"referenceId\":80,\"artifactId\":90,\"versionNo\":1,\"referenceKey\":\"script\",\"sha256\":\"digest\"}]}");
        document = new FileEvidenceApi.Document(80L,"PLT","DELIVERY_MATERIAL","SOL:solution:42","IMPLEMENTATION_PLAN","script",90L,1,"digest","config.txt",true);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void plainRemarkCannotGrantCustomerFilesButKeepsExactScriptReadable() {
        solution.setRemark("source note /api/v1/pms/solutions/42/customer-files/99/other.txt");
        when(reviews.sourceForReview(key)).thenReturn(solution);
        when(evidence.inspectDocument(1L,80L)).thenReturn(document);
        assertEquals(java.util.List.of(new SolutionReviewReadMaterials.Attachment(80L,"config.txt")),service.read(key).attachments());
        verifyNoInteractions(materials, contents);
    }
    @Test void malformedStructuredRemarkIsNotSilentlyTreatedAsPlainText() {
        solution.setScript(null); solution.setRemark("  {\"customerPlanUrl\":");
        when(reviews.sourceForReview(key)).thenReturn(solution);
        assertThrows(RuntimeException.class, () -> service.read(key));
        verifyNoInteractions(materials,evidence,contents);
    }
    @Test void foreignSolutionOrExternalCustomerUrlNeverAuthorizesADocument() {
        solution.setScript(null);
        solution.setRemark("{\"customerPlanUrl\":\"/api/v1/pms/solutions/43/customer-files/99/other.txt,https://example.invalid/customer.txt\"}");
        when(reviews.sourceForReview(key)).thenReturn(solution);
        assertTrue(service.read(key).attachments().isEmpty());
        assertThrows(RuntimeException.class, () -> service.require(new OwnerScopedFileReadPolicy.Query(1L,7L,key,document)));
        verifyNoInteractions(materials,evidence,contents);
    }
    @Test void plainRemarkDoesNotRelaxStructuredScriptOrRequirementParsing() {
        solution.setRemark("source note"); solution.setScript("{invalid");
        when(reviews.sourceForReview(key)).thenReturn(solution);
        assertThrows(RuntimeException.class, () -> service.read(key));
        solution.setScript(null); solution.setSectionData("{invalid");
        assertThrows(RuntimeException.class, () -> service.read(key));
        verifyNoInteractions(materials,evidence,contents);
    }
    @Test void plainRemarkDoesNotMaskMalformedFrozenRequirementSnapshot() {
        solution.setRemark("source note"); solution.setScript(null);
        solution.setSectionData("{\"requirementInput\":{\"revisionId\":701}}");
        var source = new SolutionSourceDO(); source.setTenantId(1L); source.setSourceSnapshot("{invalid");
        var owner = new RequirementAnalysisRevisionDO(); owner.setId(701L); owner.setTenantId(1L); owner.setProjectId(9L);
        when(reviews.sourceForReview(key)).thenReturn(solution);
        when(sources.selectBySolutionAndSource(42L,"REQUIREMENT",701L)).thenReturn(source);
        when(requirements.selectRevision(new RequirementRevisionQuery(1L,701L))).thenReturn(owner);
        assertThrows(RuntimeException.class, () -> service.read(key));
        verifyNoInteractions(materials,evidence,contents);
    }
    @Test void onlyExactReferencedScriptCanBeReadAndOtherSolutionOrVersionIsRejected() {
        when(reviews.sourceForReview(key)).thenReturn(solution);
        when(evidence.inspectDocument(1L,80L)).thenReturn(document);
        service.require(new OwnerScopedFileReadPolicy.Query(1L,7L,key,document));
        var other = new FileEvidenceApi.Document(81L,"PLT","DELIVERY_MATERIAL","SOL:solution:43","IMPLEMENTATION_PLAN","script",90L,1,"digest","other.txt",true);
        assertThrows(RuntimeException.class, () -> service.require(new OwnerScopedFileReadPolicy.Query(1L,7L,key,other)));
        var replaced = new FileEvidenceApi.Document(80L,"PLT","DELIVERY_MATERIAL","SOL:solution:42","IMPLEMENTATION_PLAN","script",90L,2,"digest2","config.txt",true);
        assertThrows(RuntimeException.class, () -> service.require(new OwnerScopedFileReadPolicy.Query(1L,7L,key,replaced)));
        verifyNoInteractions(materials, contents);
    }
    @Test void forgedTenantOrActorStopsBeforeReadingAnySourceOrFile() {
        assertThrows(RuntimeException.class, () -> service.require(new OwnerScopedFileReadPolicy.Query(2L,7L,key,document)));
        assertThrows(RuntimeException.class, () -> service.require(new OwnerScopedFileReadPolicy.Query(1L,8L,key,document)));
        verifyNoInteractions(reviews, sources, requirements, materials, evidence, contents);
    }
    @Test void revokedTaskAuthorizationStopsBeforeResolvingFiles() {
        when(reviews.sourceForReview(key)).thenThrow(new SecurityException("revoked"));
        assertThrows(SecurityException.class, () -> service.require(new OwnerScopedFileReadPolicy.Query(1L,7L,key,document)));
        verifyNoInteractions(requirements, evidence, materials, contents);
    }
    @Test void frozenRequirementTopologyMatchesExactSourceAndRejectsOtherVersions() {
        solution.setScript(null); solution.setSectionData("{\"requirementInput\":{\"revisionId\":701}}");
        var source = new SolutionSourceDO(); source.setTenantId(1L);
        source.setSourceSnapshot("{\"revision\":{\"id\":701,\"projectId\":9},\"topologyFiles\":[{\"key\":{\"ownerContext\":\"SOL\",\"objectType\":\"REQUIREMENT_ANALYSIS_REVISION\",\"objectId\":\"701\",\"purposeCode\":\"FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS\"},\"activeFacts\":[{\"artifactId\":91,\"versionNo\":1,\"referenceKey\":\"topology\",\"sha256\":\"topology-digest\"}]}]}");
        var topology = new FileEvidenceApi.Document(81L,"SOL","REQUIREMENT_ANALYSIS_REVISION","701","FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS","topology",91L,1,"topology-digest","topology.png",true);
        when(reviews.sourceForReview(key)).thenReturn(solution);
        when(sources.selectBySolutionAndSource(42L,"REQUIREMENT",701L)).thenReturn(source);
        var revision = new RequirementAnalysisRevisionDO(); revision.setId(701L); revision.setTenantId(1L); revision.setProjectId(9L);
        when(requirements.selectRevision(new RequirementRevisionQuery(1L,701L))).thenReturn(revision);
        when(evidence.inspectDocument(new FileEvidenceApi.ReferenceQuery(1L,"SOL","REQUIREMENT_ANALYSIS_REVISION","701","FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS","topology"))).thenReturn(topology);
        service.require(new OwnerScopedFileReadPolicy.Query(1L,7L,key,topology));
        var anotherRevision = new FileEvidenceApi.Document(81L,"SOL","REQUIREMENT_ANALYSIS_REVISION","702","FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS","topology",91L,1,"topology-digest","topology.png",true);
        assertThrows(RuntimeException.class, () -> service.require(new OwnerScopedFileReadPolicy.Query(1L,7L,key,anotherRevision)));
        var replaced = new FileEvidenceApi.Document(81L,"SOL","REQUIREMENT_ANALYSIS_REVISION","701","FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS","topology",91L,2,"changed","topology.png",true);
        assertThrows(RuntimeException.class, () -> service.require(new OwnerScopedFileReadPolicy.Query(1L,7L,key,replaced)));
    }
    @Test void actualSerializedRequirementSnapshotKeepsHiddenProjectOwnershipAndFrozenContent() {
        solution.setScript(null); solution.setSectionData("{\"requirementInput\":{\"revisionId\":701}}");
        var revision = new RequirementAnalysisRevisionDO();
        revision.setId(701L); revision.setTenantId(1L); revision.setProjectId(9L); revision.setProjectBackground("frozen background");
        var source = new SolutionSourceDO(); source.setTenantId(1L);
        source.setSourceSnapshot(JsonUtils.toJsonString(java.util.Map.of("revision",revision)));
        assertFalse(JsonUtils.parseTree(source.getSourceSnapshot()).path("revision").has("projectId"));
        revision.setProjectBackground("current content must not replace frozen content");
        when(reviews.sourceForReview(key)).thenReturn(solution);
        when(sources.selectBySolutionAndSource(42L,"REQUIREMENT",701L)).thenReturn(source);
        when(requirements.selectRevision(new RequirementRevisionQuery(1L,701L))).thenReturn(revision);
        assertEquals("frozen background",service.read(key).requirement().get("projectBackground"));
        verifyNoInteractions(materials,evidence,contents);
    }
    @Test void missingOrWrongTenantProjectOrRevisionOwnerCannotAuthorizeFrozenMaterials() {
        solution.setScript(null); solution.setSectionData("{\"requirementInput\":{\"revisionId\":701}}");
        var source = new SolutionSourceDO(); source.setTenantId(1L);
        source.setSourceSnapshot("{\"revision\":{\"id\":701,\"projectBackground\":\"frozen\"}}");
        when(reviews.sourceForReview(key)).thenReturn(solution);
        when(sources.selectBySolutionAndSource(42L,"REQUIREMENT",701L)).thenReturn(source);
        assertThrows(RuntimeException.class, () -> service.read(key));
        source.setTenantId(2L);
        assertThrows(RuntimeException.class, () -> service.read(key));
        source.setTenantId(1L);
        var owner = new RequirementAnalysisRevisionDO(); owner.setId(701L); owner.setTenantId(2L); owner.setProjectId(9L);
        when(requirements.selectRevision(new RequirementRevisionQuery(1L,701L))).thenReturn(owner);
        assertThrows(RuntimeException.class, () -> service.read(key));
        owner.setTenantId(1L); owner.setProjectId(10L);
        assertThrows(RuntimeException.class, () -> service.read(key));
        owner.setProjectId(9L); owner.setId(702L);
        assertThrows(RuntimeException.class, () -> service.read(key));
        verifyNoInteractions(materials,evidence,contents);
    }
    @Test void frozenSnapshotRevisionOrExplicitProjectMismatchIsStillRejected() {
        solution.setScript(null); solution.setSectionData("{\"requirementInput\":{\"revisionId\":701}}");
        var source = new SolutionSourceDO(); source.setTenantId(1L);
        var owner = new RequirementAnalysisRevisionDO(); owner.setId(701L); owner.setTenantId(1L); owner.setProjectId(9L);
        when(reviews.sourceForReview(key)).thenReturn(solution);
        when(sources.selectBySolutionAndSource(42L,"REQUIREMENT",701L)).thenReturn(source);
        when(requirements.selectRevision(new RequirementRevisionQuery(1L,701L))).thenReturn(owner);
        source.setSourceSnapshot("{\"revision\":{\"id\":702}}");
        assertThrows(RuntimeException.class, () -> service.read(key));
        source.setSourceSnapshot("{\"revision\":{\"id\":701,\"projectId\":10}}");
        assertThrows(RuntimeException.class, () -> service.read(key));
        verifyNoInteractions(materials,evidence,contents);
    }
    @Test void materialReadStartsRealSpringTransactionRequiredByFileEvidence() {
        var database = new org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2).build();
        try {
            var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(database);
            var artifacts = mock(cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper.class);
            var versions = mock(cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper.class);
            var references = mock(cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper.class);
            var artifact = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO();
            artifact.setLifecycleStatusCode("ACTIVE"); artifact.setName("config.txt");
            var version = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO();
            version.setAvailabilityStatusCode("AVAILABLE"); version.setSha256("digest");
            var reference = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO();
            reference.setArtifactId(90L); reference.setFileVersionNo(1); reference.setStatusCode("ACTIVE");
            reference.setOwnerContext("PLT"); reference.setObjectType("DELIVERY_MATERIAL"); reference.setObjectId("SOL:solution:42");
            reference.setPurposeCode("IMPLEMENTATION_PLAN"); reference.setReferenceKey("script");
            when(artifacts.selectOne(any())).thenReturn(artifact); when(versions.selectOne(any())).thenReturn(version);
            when(references.selectIdentity(any())).thenReturn(reference);
            var attributes = new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource();
            var evidenceFactory = new org.springframework.aop.framework.ProxyFactory(
                    new cn.iocoder.yudao.module.pms.platform.service.file.FileEvidenceService(artifacts,versions,references));
            evidenceFactory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,attributes));
            var actualEvidence = (FileEvidenceApi) evidenceFactory.getProxy();
            assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> actualEvidence.inspectDocument(1L,80L));
            when(reviews.sourceForReview(key)).thenReturn(solution);
            var materialsFactory = new org.springframework.aop.framework.ProxyFactory(new SolutionReviewReadMaterials(reviews,sources,requirements,materials,actualEvidence,contents));
            materialsFactory.setProxyTargetClass(true);
            materialsFactory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,attributes));
            var actualMaterials = (SolutionReviewReadMaterials) materialsFactory.getProxy();
            assertEquals(java.util.List.of(new SolutionReviewReadMaterials.Attachment(80L,"config.txt")),actualMaterials.read(key).attachments());
        } finally { database.shutdown(); }
    }
}
