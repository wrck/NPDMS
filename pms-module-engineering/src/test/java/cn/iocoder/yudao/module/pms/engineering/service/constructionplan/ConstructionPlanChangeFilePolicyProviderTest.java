package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectDurationOperatorApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConstructionPlanChangeFilePolicyProviderTest {

    @Mock ConstructionPlanMapper planMapper;
    @Mock ConstructionPlanChangeMapper changeMapper;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectDurationOperatorApi durationOperatorApi;
    @Mock ProjectParticipantFactApi participantFactApi;

    @Mock cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi qualifications;
    @org.mockito.InjectMocks private ConstructionPlanChangeFilePolicyProvider provider;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(durationOperatorApi.inspect(any())).thenReturn(new ProjectDurationOperatorApi.Fact(100L,9L,"ACTIVE",3L,"REGIONAL_SERVICE_MANAGER"));
        org.mockito.Mockito.lenient().when(durationOperatorApi.lockAndRevalidate(any())).thenReturn(new ProjectDurationOperatorApi.Fact(100L,9L,"ACTIVE",3L,"REGIONAL_SERVICE_MANAGER"));

    }

    private cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Fact managementFact() {
        return new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Fact(
                new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Query(0L,100L,9L),
                cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Source.PROJECT_MEMBER,
                new ProjectParticipantFact(100L,9L,Set.of("SERVICE_MANAGER"),"PRIMARY","ACTIVE","S1",3L,3L),null);
    }
    @Test void confirmedProjectSmWithoutRawReaderFactUsesUnifiedOwner() {
        stubCurrent(ConstructionPlanChangeDO.STATUS_DRAFT,"SERVICE_MANAGER");
        when(participantFactApi.inspect(any())).thenReturn(null);
        org.mockito.Mockito.lenient().when(qualifications.inspect(any())).thenReturn(managementFact());
        org.mockito.Mockito.lenient().when(durationOperatorApi.inspect(any())).thenThrow(new IllegalStateException("legacy PM/region path rejected"));
        assertTrue(provider.inspect(query(FileActionCodes.READ)).allowed());
        org.mockito.Mockito.verify(qualifications).inspect(any());
    }
    @Test void unknownRawFactFailureDoesNotBecomeAnotherQualification() {
        stubCurrent(ConstructionPlanChangeDO.STATUS_DRAFT,"PROJECT_MANAGER");
        when(participantFactApi.inspect(any())).thenThrow(new IllegalStateException("provider unavailable"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->provider.inspect(query(FileActionCodes.READ)));
        org.mockito.Mockito.verifyNoInteractions(qualifications);
    }
    @Test void selectedRawReaderLockRevocationCannotFallBackToManagement() {
        stubCurrent(ConstructionPlanChangeDO.STATUS_DRAFT,"PROJECT_MANAGER");
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(100L,7L,Set.of(100L),Set.of()));
        org.mockito.Mockito.lenient().when(planMapper.selectForUpdate(any())).thenReturn(plan());
        org.mockito.Mockito.lenient().when(changeMapper.selectForUpdate(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(participantFactApi.lockAndRevalidate(any())).thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(
                ProjectParticipantFactApi.SCOPE_DENIED_ERROR_CODE,"revoked"));
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                ()->provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(0L,9L,"SOL","CONSTRUCTION_PLAN_CHANGE","801",
                        "CUSTOMER_DELAY_EVIDENCE","customer-delay",FileActionCodes.READ,7L)));
        org.mockito.Mockito.verifyNoInteractions(qualifications,durationOperatorApi);
    }
    @Test void fallbackReaderRevalidatesTheExactUnifiedSource() {
        when(changeMapper.selectByObjectId(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(planMapper.selectByLockQuery(any())).thenReturn(plan());
        when(planMapper.selectForUpdate(any())).thenReturn(plan());
        when(changeMapper.selectForUpdate(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(100L,7L,Set.of(100L),Set.of()));
        var observed = managementFact();
        when(qualifications.inspect(any())).thenReturn(observed);
        assertTrue(provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(0L,9L,"SOL","CONSTRUCTION_PLAN_CHANGE","801",
                "CUSTOMER_DELAY_EVIDENCE","customer-delay",FileActionCodes.READ,7L)).allowed());
        org.mockito.Mockito.verify(qualifications).lockAndRevalidate(new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Revalidation(
                observed,3L,"ACTIVE",null));
        org.mockito.Mockito.verify(participantFactApi,org.mockito.Mockito.never()).lockAndRevalidate(any());
        org.mockito.Mockito.verifyNoInteractions(durationOperatorApi);
    }
    @Test void closedHistoricalReaderKeepsOriginalFactPath() {
        stubCurrent(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL,"PROJECT_MANAGER");
        when(participantFactApi.inspect(any())).thenReturn(new ProjectParticipantFact(100L,9L,Set.of("PROJECT_MANAGER"),"PRIMARY","NORMAL_CLOSED","S6",3L,3L));
        assertTrue(provider.inspect(query(FileActionCodes.READ)).allowed());
        org.mockito.Mockito.verifyNoInteractions(qualifications,durationOperatorApi);
    }

    @Test
    void regionalManagerMayReadEvidenceAfterUploadingWithoutBecomingAProjectMember() {
        when(changeMapper.selectByObjectId(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(planMapper.selectByLockQuery(any())).thenReturn(plan());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,7L,Set.of(100L),Set.of()));
        when(participantFactApi.inspect(any())).thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(ProjectParticipantFactApi.SCOPE_DENIED_ERROR_CODE,"No project membership"));
        org.mockito.Mockito.lenient().when(qualifications.inspect(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Fact(
                new cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Query(0L,100L,9L),
                cn.iocoder.yudao.module.pms.project.api.participant.ProjectManagementQualificationApi.Source.REGIONAL_MANAGER,null,
                new cn.iocoder.yudao.module.pms.project.api.participant.ProjectRegionalManagerFactApi.Fact(100L,9L,3L)));
        assertTrue(provider.inspect(query(FileActionCodes.READ)).allowed());
        org.mockito.Mockito.verify(qualifications).inspect(any());
    }

    @Test
    void crossRegionCannotUploadOrReadEvidence() {
        when(changeMapper.selectByObjectId(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(planMapper.selectByLockQuery(any())).thenReturn(plan());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,7L,Set.of(100L),Set.of()));
        when(durationOperatorApi.inspect(any())).thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(403,"Not in region"));
        org.mockito.Mockito.lenient().when(qualifications.inspect(any())).thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(403,"Not in region"));
        org.mockito.Mockito.lenient().when(participantFactApi.inspect(any())).thenThrow(new cn.iocoder.yudao.framework.common.exception.ServiceException(ProjectParticipantFactApi.SCOPE_DENIED_ERROR_CODE,"No project membership"));
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->provider.inspect(query(FileActionCodes.UPLOAD)));
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->provider.inspect(query(FileActionCodes.READ)));
    }

    @Test
    void regionalManagerCannotReplaceFrozenPendingEvidence() {
        when(changeMapper.selectByObjectId(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL));
        when(planMapper.selectByLockQuery(any())).thenReturn(plan());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L,7L,Set.of(100L),Set.of()));
        assertFalse(provider.inspect(query(FileActionCodes.REPLACE)).allowed());
    }

    @Test
    void allowsProjectManagerToUploadToDraftSingleSlot() {
        stubCurrent(ConstructionPlanChangeDO.STATUS_DRAFT,
                ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);

        var fact = provider.inspect(query(FileActionCodes.UPLOAD));

        assertTrue(fact.allowed());
        assertEquals(7L, fact.scopeVersion());
        assertEquals("MUTABLE", fact.referenceMutability());
        assertEquals("SINGLE", fact.cardinality());
        assertEquals(Set.of("CUSTOMER_DELAY_EVIDENCE"), fact.allowedCategoryCodes());
    }

    @Test
    void locksProjectFactsAndReturnsSameScopeForReference() {
        when(changeMapper.selectByObjectId(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(planMapper.selectByLockQuery(any())).thenReturn(plan());
        when(planMapper.selectForUpdate(any())).thenReturn(plan());
        when(changeMapper.selectForUpdate(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(
                new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of()));

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "SOL", "CONSTRUCTION_PLAN_CHANGE", "801",
                "CUSTOMER_DELAY_EVIDENCE", "customer-delay", FileActionCodes.REFERENCE, 7L));

        assertTrue(fact.allowed());
        assertEquals(7L, fact.scopeVersion());
    }

    @Test
    void uploadCompletionAndArchiveCanLockTheChangeNamespace() {
        when(changeMapper.selectByObjectId(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(planMapper.selectByLockQuery(any())).thenReturn(plan());
        when(planMapper.selectForUpdate(any())).thenReturn(plan());
        when(changeMapper.selectForUpdate(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(
                new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of()));

        for (String action : java.util.List.of(FileActionCodes.UPLOAD, FileActionCodes.ARCHIVE)) {
            var fact = provider.lockAndRevalidateReferenceSet(
                    new FileBusinessObjectReferenceSetRevalidationQuery(0L, 9L,
                            new FileReferenceSetKey("SOL", "CONSTRUCTION_PLAN_CHANGE", "801",
                                    "CUSTOMER_DELAY_EVIDENCE"), action, 7L));
            assertTrue(fact.allowed());
        }
    }

    @Test
    void makesPendingApprovalReferenceImmutable() {
        stubCurrent(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL,
                ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);

        var read = provider.inspect(query(FileActionCodes.READ));
        var replace = provider.inspect(query(FileActionCodes.REPLACE));

        assertTrue(read.allowed());
        assertEquals("IMMUTABLE", read.referenceMutability());
        assertFalse(replace.allowed());
    }

    private void stubCurrent(String status, String role) {
        when(changeMapper.selectByObjectId(any())).thenReturn(change(status));
        when(planMapper.selectByLockQuery(any())).thenReturn(plan());
        org.mockito.Mockito.lenient().when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of()));
        ProjectParticipantFact participant = new ProjectParticipantFact(
                100L, 9L, Set.of(role), "PRIMARY", "ACTIVE", "S1", 3L, 3L);
        org.mockito.Mockito.lenient().when(participantFactApi.inspect(any())).thenReturn(participant);
    }

    private FileBusinessObjectPolicyQuery query(String action) {
        return new FileBusinessObjectPolicyQuery(0L, 9L, "SOL", "CONSTRUCTION_PLAN_CHANGE",
                "801", "CUSTOMER_DELAY_EVIDENCE", "customer-delay", action);
    }

    private ConstructionPlanDO plan() {
        ConstructionPlanDO row = new ConstructionPlanDO();
        row.setId(501L);
        row.setTenantId(0L);
        row.setProjectId(100L);
        return row;
    }

    private ConstructionPlanChangeDO change(String status) {
        ConstructionPlanChangeDO row = new ConstructionPlanChangeDO();
        row.setId(801L);
        row.setTenantId(0L);
        row.setPlanId(501L);
        row.setStatusCode(status);
        return row;
    }
}
