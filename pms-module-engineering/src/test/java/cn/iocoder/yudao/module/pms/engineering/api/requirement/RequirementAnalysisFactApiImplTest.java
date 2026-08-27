package cn.iocoder.yudao.module.pms.engineering.api.requirement;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_FILE_FACT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementAnalysisFactApiImplTest {

    private static final String SLOT = "2fce3d44-109d-47be-b15a-5ea09fda1a0f";
    private static final FileFactVersion FILE_VERSION = new FileFactVersion(2, 3, 4);

    @Mock RequirementAnalysisRootMapper rootMapper;
    @Mock RequirementAnalysisSectionMapper sectionMapper;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectOrganizationFactApi organizationFactApi;
    @Mock FileArtifactApi fileArtifactApi;

    private RequirementAnalysisFactApiImpl api;

    @BeforeEach
    void setUp() {
        api = new RequirementAnalysisFactApiImpl(
                rootMapper, sectionMapper, permissionApi, projectScopeApi, organizationFactApi, fileArtifactApi);
        TenantContextHolder.setTenantId(0L);
        LoginUser loginUser = new LoginUser().setId(9L).setTenantId(0L).setUserType(2);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void inspectReturnsExplicitHistoricalCompletedFactAndCurrentPointer() {
        stubProject();
        PreparationDO historical = root(501L, 1, null, "COMPLETED");
        PreparationDO effective = root(502L, 2, 1, "COMPLETED");
        when(rootMapper.selectById(any())).thenReturn(historical);
        when(rootMapper.selectEffective(any())).thenReturn(effective);
        when(sectionMapper.selectList(any())).thenReturn(List.of(section()));
        when(fileArtifactApi.inspect(any())).thenReturn(fileFact(FILE_VERSION));

        var fact = api.inspect(new RequirementAnalysisFactQuery(100L, 501L));

        assertEquals(501L, fact.preparationId());
        assertEquals("COMPLETED", fact.status());
        assertFalse(fact.currentEffective());
        assertEquals(502L, fact.currentEffectivePreparationId());
        assertEquals(2, fact.currentEffectiveBusinessVersion());
        assertEquals(3, fact.projectVersion());
        assertEquals(1, fact.orderedSectionFacts().size());
        assertEquals(1, fact.fileFacts().size());
        assertEquals(fact.orderedSectionFacts(), fact.factVector().orderedSectionFacts());
    }

    @Test
    void inspectUsesOneReadOnlyTransactionSnapshot() throws Exception {
        Transactional transactional = RequirementAnalysisFactApiImpl.class
                .getMethod("inspect", RequirementAnalysisFactQuery.class)
                .getAnnotation(Transactional.class);

        assertTrue(transactional.readOnly());
    }

    @Test
    void queryPermissionIsRequiredBeforeSolOrPltFacts() {
        when(permissionApi.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(false);

        assertThrows(ServiceException.class,
                () -> api.inspect(new RequirementAnalysisFactQuery(100L, 501L)));

        verifyNoInteractions(projectScopeApi, organizationFactApi, rootMapper, sectionMapper, fileArtifactApi);
    }

    @Test
    void inspectWithoutCurrentEffectiveReturnsExplicitEmptyResultAndDoesNotReadFiles() {
        stubProject();
        when(rootMapper.selectEffective(any())).thenReturn(null);

        var fact = api.inspect(new RequirementAnalysisFactQuery(100L, null));

        assertEquals(null, fact);
        verifyNoInteractions(sectionMapper, fileArtifactApi);
    }

    @Test
    void inspectRejectsDraftAndChangedExactFileFact() {
        stubProject();
        when(rootMapper.selectById(any())).thenReturn(root(501L, 1, 1, "DRAFT"));
        ServiceException draftError = assertThrows(ServiceException.class,
                () -> api.inspect(new RequirementAnalysisFactQuery(100L, 501L)));
        assertEquals(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE.getCode(), draftError.getCode());

        when(rootMapper.selectById(any())).thenReturn(root(501L, 1, 1, "COMPLETED"));
        when(rootMapper.selectEffective(any())).thenReturn(root(501L, 1, 1, "COMPLETED"));
        when(sectionMapper.selectList(any())).thenReturn(List.of(section()));
        when(fileArtifactApi.inspect(any())).thenReturn(fileFact(new FileFactVersion(2, 4, 4)));
        ServiceException fileError = assertThrows(ServiceException.class,
                () -> api.inspect(new RequirementAnalysisFactQuery(100L, 501L)));
        assertEquals(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID.getCode(), fileError.getCode());
    }

    @Test
    void lockRevalidatesProjThenSolThenExactFilesAndComparesCompleteVector() {
        stubProject();
        PreparationDO selected = root(501L, 1, 1, "COMPLETED");
        when(rootMapper.selectById(any())).thenReturn(selected);
        when(rootMapper.selectEffective(any())).thenReturn(selected);
        when(sectionMapper.selectList(any())).thenReturn(List.of(section()));
        when(fileArtifactApi.inspect(any())).thenReturn(fileFact(FILE_VERSION));
        var inspected = api.inspect(new RequirementAnalysisFactQuery(100L, 501L));
        clearInvocations(projectScopeApi, organizationFactApi, rootMapper, sectionMapper, fileArtifactApi);

        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(organizationFactApi.lockAndRevalidate(any())).thenReturn(project());
        when(rootMapper.selectForUpdate(any())).thenReturn(selected);
        when(sectionMapper.selectListForUpdate(any())).thenReturn(List.of(section()));
        when(rootMapper.selectEffectiveForUpdate(any())).thenReturn(selected);
        when(fileArtifactApi.lockAndRevalidate(any())).thenReturn(fileFact(FILE_VERSION));

        var locked = api.lockAndRevalidate(new RequirementAnalysisFactRevalidationQuery(100L, 501L, 1,
                5, 3, 17L, inspected.factVector()));

        assertTrue(locked.currentEffective());
        assertEquals(inspected.factVector(), locked.factVector());
        InOrder order = inOrder(projectScopeApi, organizationFactApi, rootMapper, sectionMapper, fileArtifactApi);
        order.verify(projectScopeApi).resolveCurrent(any());
        order.verify(projectScopeApi).lockAndRevalidate(any());
        order.verify(organizationFactApi).lockAndRevalidate(any());
        order.verify(rootMapper).selectForUpdate(any());
        order.verify(sectionMapper).selectListForUpdate(any());
        order.verify(rootMapper).selectEffectiveForUpdate(any());
        order.verify(fileArtifactApi).lockAndRevalidate(any());
    }

    private void stubProject() {
        when(permissionApi.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(organizationFactApi.inspect(any())).thenReturn(project());
    }

    private ProjectScopeResult scope() {
        return new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of());
    }

    private ProjectOrganizationFact project() {
        return new ProjectOrganizationFact(100L, 3, 10L, 20L, "D20");
    }

    private PreparationDO root(Long id, Integer businessVersion, Integer effectiveMarker, String status) {
        PreparationDO row = new PreparationDO();
        row.setId(id);
        row.setTenantId(0L);
        row.setProjectId(100L);
        row.setBusinessVersion(businessVersion);
        row.setEffectiveMarker(effectiveMarker);
        row.setStatusCode(status);
        row.setContentVersion(5);
        row.setTemplateRevisionId(17L);
        row.setCompletedBy(9L);
        row.setCompletedAt(LocalDateTime.of(2026, 8, 27, 10, 0));
        return row;
    }

    private RequirementAnalysisSectionDO section() {
        RequirementAnalysisSectionDO row = new RequirementAnalysisSectionDO();
        row.setId(701L);
        row.setPreparationId(501L);
        row.setTenantId(0L);
        row.setSectionCode("CURRENT_SITUATION");
        row.setSectionName("现状说明");
        row.setSectionKindCode("CORE");
        row.setFieldTypeCode("RICH_TEXT");
        row.setRequiredFlag(true);
        row.setSortOrder(1);
        row.setSchemaSnapshot("{}");
        row.setValueSnapshot("{\"text\":\"frozen\"}");
        row.setAttachmentReferenceSnapshot("[{\"artifactId\":801,\"versionNo\":1,\"referenceKey\":\""
                + SLOT + "\",\"fileFactVersion\":{\"artifactVersion\":2,\"referenceVersion\":3,"
                + "\"availabilityVersion\":4},\"scopeVersion\":7}]");
        row.setVersion(2);
        return row;
    }

    private FileArtifactVersionFact fileFact(FileFactVersion version) {
        return new FileArtifactVersionFact(801L, 1, SLOT, "SECTION_ATTACHMENT", "evidence.pdf", 10L,
                "application/pdf", "sha", "AVAILABLE", "ACTIVE", version, 7L);
    }
}
