package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementAnalysisFilePolicyProviderTest {

    private static final String SLOT = "2fce3d44-109d-47be-b15a-5ea09fda1a0f";

    @Mock RequirementAnalysisRootMapper rootMapper;
    @Mock RequirementAnalysisSectionMapper sectionMapper;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;

    private RequirementAnalysisFilePolicyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RequirementAnalysisFilePolicyProvider(
                rootMapper, sectionMapper, permissionApi, projectScopeApi, participantFactApi);
    }

    @Test
    void draftCurrentManagerCanMutateMultipleAttachmentSlots() {
        stubContext("DRAFT");
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(participantFactApi.inspect(any())).thenReturn(manager());

        var fact = provider.inspect(query(FileActionCodes.REPLACE));

        assertTrue(fact.allowed());
        assertEquals("MUTABLE", fact.referenceMutability());
        assertEquals("MULTIPLE", fact.cardinality());
        assertEquals(Set.of("REQUIREMENT_ANALYSIS_ATTACHMENT"), fact.allowedCategoryCodes());
    }

    @Test
    void completedAuthorizedMemberCanOnlyRead() {
        stubContext("COMPLETED");
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_QUERY)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());

        assertTrue(provider.inspect(query(FileActionCodes.PREVIEW)).allowed());
        assertFalse(provider.inspect(query(FileActionCodes.REPLACE)).allowed());
        assertFalse(provider.inspect(query(FileActionCodes.ARCHIVE)).allowed());
        verifyNoInteractions(participantFactApi);
    }

    @Test
    void invalidSlotKeyFailsClosedBeforeProjectFacts() {
        stubContext("DRAFT");

        var fact = provider.inspect(new FileBusinessObjectPolicyQuery(0L, 9L, "SOL",
                "REQUIREMENT_ANALYSIS_SECTION", "701", "SECTION_ATTACHMENT", "not-a-uuid",
                FileActionCodes.UPLOAD));

        assertFalse(fact.allowed());
        verifyNoInteractions(permissionApi, projectScopeApi, participantFactApi);
    }

    @Test
    void lockRevalidatesManagerBeforeSolRowsAndRejectsArchive() {
        stubContext("DRAFT");
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(participantFactApi.inspect(any())).thenReturn(manager());
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(manager());
        when(rootMapper.selectForUpdate(any())).thenReturn(root("DRAFT"));
        when(sectionMapper.selectForUpdate(any())).thenReturn(section());
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);

        var fact = provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "SOL", "REQUIREMENT_ANALYSIS_SECTION", "701", "SECTION_ATTACHMENT", SLOT,
                FileActionCodes.REFERENCE, 7L));

        assertTrue(fact.allowed());
        assertFalse(provider.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                0L, 9L, "SOL", "REQUIREMENT_ANALYSIS_SECTION", "701", "SECTION_ATTACHMENT", SLOT,
                FileActionCodes.INVALIDATE, 7L)).allowed());
    }

    private void stubContext(String status) {
        when(sectionMapper.selectByIdentity(any())).thenReturn(section());
        when(rootMapper.selectById(any())).thenReturn(root(status));
    }

    private FileBusinessObjectPolicyQuery query(String action) {
        return new FileBusinessObjectPolicyQuery(0L, 9L, "SOL", "REQUIREMENT_ANALYSIS_SECTION",
                "701", "SECTION_ATTACHMENT", SLOT, action);
    }

    private ProjectScopeResult scope() {
        return new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of());
    }

    private ProjectParticipantFact manager() {
        return new ProjectParticipantFact(100L, 9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "PRIMARY", "ACTIVE", "S1", 3, 11L);
    }

    private PreparationDO root(String status) {
        PreparationDO row = new PreparationDO();
        row.setId(501L);
        row.setTenantId(0L);
        row.setProjectId(100L);
        row.setStatusCode(status);
        return row;
    }

    private RequirementAnalysisSectionDO section() {
        RequirementAnalysisSectionDO row = new RequirementAnalysisSectionDO();
        row.setId(701L);
        row.setTenantId(0L);
        row.setPreparationId(501L);
        return row;
    }
}
