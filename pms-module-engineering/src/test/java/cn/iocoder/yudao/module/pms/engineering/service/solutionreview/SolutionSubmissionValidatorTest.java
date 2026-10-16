package cn.iocoder.yudao.module.pms.engineering.service.solutionreview;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionSourceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionSourceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class SolutionSubmissionValidatorTest {
    @Mock RequirementAnalysisMapper requirements;
    @Mock SolutionSourceMapper sources;
    @Mock FileEvidenceApi files;
    @Mock cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionCustomerDocumentService customerDocuments;
    @InjectMocks SolutionSubmissionValidator validator;
    SolutionDO solution;
    RequirementAnalysisRevisionDO revision;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        solution = new SolutionDO(); solution.setId(42L); solution.setProjectId(9L); solution.setSolutionType("IMPLEMENTATION");
        solution.setBackground("背景"); solution.setTarget("目标");
        solution.setTopology("{\"asIsUrl\":\"topology.png\",\"deploy\":[{\"deviceName\":\"路由器\",\"location\":\"隔离机柜\"}]}");
        solution.setScript("{\"references\":[{\"section\":\"full\",\"referenceId\":4,\"artifactId\":5,\"versionNo\":1,\"referenceKey\":\"solution-script-full-test\",\"sha256\":\"digest\"}]}");
        solution.setSectionData("{\"requirementInput\":{\"revisionId\":7}}");
        revision = new RequirementAnalysisRevisionDO(); revision.setId(7L); revision.setEntityId(8L); revision.setProjectId(9L);
        revision.setRevisionState("FROZEN"); revision.setRevisionNo(1);
        when(requirements.selectRevision(any())).thenReturn(revision);
        when(requirements.lockRevision(any())).thenReturn(revision);
        when(requirements.lockCurrent(any())).thenReturn(new RequirementAnalysisDO());
        when(requirements.lockEffective(any())).thenReturn(revision);
        when(files.inspectDocument(1L,4L)).thenReturn(document("SOL:solution:42",true));
        when(files.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true,null,1,1,1));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private FileEvidenceApi.Document document(String owner, boolean available) {
        return new FileEvidenceApi.Document(4L,"PLT","DELIVERY_MATERIAL",owner,"IMPLEMENTATION_PLAN","solution-script-full-test",5L,1,"digest","script.txt",available);
    }
    @Test void rejectsBlankRichTextAndEmptyJsonBeforeAnySourceOrFileWrite() {
        solution.setBackground("<p>&nbsp;<br></p>"); solution.setTarget(" "); solution.setTopology("{}"); solution.setScript("{}");
        var error = assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        for (String field : new String[]{"项目背景","项目目标","网络拓扑","部署方案","配置脚本文件"}) assertTrue(error.getMessage().contains(field));
        verifyNoInteractions(files,requirements,sources);
    }
    @Test void textWithoutUploadedFileDoesNotMeetScriptRequirement() {
        solution.setScript("{\"full\":\"show version\"}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        verifyNoInteractions(sources);
    }
    @Test void customerFlagWithoutNativeDocumentCannotBypassOnlineRequiredFields() {
        solution.setRemark("{\"hasCustomerPlan\":\"yes\",\"customerPlanUrl\":\"https://unverified/file.txt\"}");
        solution.setBackground("");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        verifyNoInteractions(customerDocuments);
    }
    @Test void completeCustomerDocumentStillUsesExistingOwnerValidation() {
        solution.setRemark("{\"hasCustomerPlan\":\"yes\",\"customerPlanUrl\":\"/api/v1/pms/solutions/42/customer-files/1/customer.txt\"}");
        solution.setBackground("");
        validator.validateAndFreezeSources(solution);
        verify(customerDocuments).validatePointer(42L, solution.getRemark());
        verifyNoInteractions(files,requirements,sources);
    }
    @Test void rejectsOtherSolutionFile() {
        when(files.inspectDocument(1L,4L)).thenReturn(document("SOL:solution:41",true));
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        verifyNoInteractions(sources);
    }
    @Test void rejectsUnavailableOrChangedFile() {
        when(files.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false,"deleted",1,2,1));
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        verifyNoInteractions(sources);
    }
    @Test void rejectsRequirementFromOtherProject() {
        revision.setProjectId(10L);
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        verifyNoInteractions(sources);
    }
    @Test void rejectsDraftOrMissingEffectiveRequirement() {
        revision.setRevisionState("DRAFT");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        revision.setRevisionState("FROZEN"); when(requirements.lockEffective(any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        verifyNoInteractions(sources);
    }
    @Test void changedInputRequiresExplanationForExactLatestRevision() {
        var next = new RequirementAnalysisRevisionDO(); next.setId(11L); next.setRevisionState("FROZEN");
        when(requirements.lockEffective(any())).thenReturn(next);
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        solution.setSectionData("{\"requirementInput\":{\"revisionId\":7,\"acknowledgedRevisionId\":10,\"differenceReason\":\"保留原方案\"}}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndFreezeSources(solution));
        solution.setSectionData("{\"requirementInput\":{\"revisionId\":7,\"acknowledgedRevisionId\":11,\"differenceReason\":\"新需求不影响本次实施范围\"}}");
        validator.validateAndFreezeSources(solution);
        verify(sources).insert(any(SolutionSourceDO.class));
    }
    @Test void freezesVerifiedSourceAndDoesNotReplacePriorSnapshot() {
        solution.setRemark("源方案 SOL-41；审批实例 previous-review");
        validator.validateAndFreezeSources(solution);
        var saved = ArgumentCaptor.forClass(SolutionSourceDO.class);
        verify(sources).insert(saved.capture());
        assertEquals(7L,saved.getValue().getSourceId());
        assertTrue(saved.getValue().getSourceSnapshot().contains("referencedAt"));
        when(sources.selectBySolutionAndSource(42L,"REQUIREMENT",7L)).thenReturn(saved.getValue());
        validator.validateAndFreezeSources(solution);
        verify(sources,times(1)).insert(any(SolutionSourceDO.class));
    }
}
