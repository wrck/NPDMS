package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
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
    @Mock ProjectParticipantFactApi participantFactApi;

    private ConstructionPlanChangeFilePolicyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ConstructionPlanChangeFilePolicyProvider(
                planMapper, changeMapper, projectScopeApi, participantFactApi);
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
        stubCurrent(ConstructionPlanChangeDO.STATUS_DRAFT,
                ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
        when(planMapper.selectForUpdate(any())).thenReturn(plan());
        when(changeMapper.selectForUpdate(any())).thenReturn(change(ConstructionPlanChangeDO.STATUS_DRAFT));
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(new ProjectParticipantFact(
                100L, 9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "PRIMARY", "ACTIVE", "S1", 3, 3L));

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "SOL", "CONSTRUCTION_PLAN_CHANGE", "801",
                "CUSTOMER_DELAY_EVIDENCE", "customer-delay", FileActionCodes.REFERENCE, 7L));

        assertTrue(fact.allowed());
        assertEquals(7L, fact.scopeVersion());
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
        when(planMapper.selectById(any())).thenReturn(plan());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of()));
        ProjectParticipantFact participant = new ProjectParticipantFact(
                100L, 9L, Set.of(role), "PRIMARY", "ACTIVE", "S1", 3, 3L);
        when(participantFactApi.inspect(any())).thenReturn(participant);
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
