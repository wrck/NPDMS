package cn.iocoder.yudao.module.pms.engineering.api.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisEntityFactApiTest {
    private final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectOrganizationFactApi projects = mock(ProjectOrganizationFactApi.class);
    private final ProjectWorkBindingFactApi bindings = mock(ProjectWorkBindingFactApi.class);
    private final RequirementAnalysisEntityQueryService queries = mock(RequirementAnalysisEntityQueryService.class);
    private final RequirementAnalysisRevisionFiles files = mock(RequirementAnalysisRevisionFiles.class);
    private final RequirementAnalysisEntityFactApi api = new RequirementAnalysisEntityFactApiImpl(mapper, permissions, scopes, projects, bindings, queries, files);
    private final RequirementAnalysisEntityFactApi.Query query = new RequirementAnalysisEntityFactApi.Query(100L, 500L, 501L);
    private RequirementAnalysisRevisionDO row;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(9L); user.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(true);
        var scope = new ProjectScopeResult(100L, 8L, Set.of(100L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope);
        when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        var project = new ProjectOrganizationFact(100L, 3, 10L, 20L, "D20");
        when(projects.inspect(any())).thenReturn(project);
        when(projects.lockAndRevalidate(any())).thenReturn(project);
        var binding = binding();
        when(bindings.inspectTask(any())).thenReturn(binding);
        when(bindings.lockAndRevalidate(any())).thenReturn(binding);
        row = new RequirementAnalysisRevisionDO();
        row.setTenantId(1L); row.setId(501L); row.setEntityId(500L); row.setProjectId(100L);
        row.setRevisionNo(1); row.setVersion(4); row.setRevisionState("FROZEN"); row.setEffectiveMarker(1);
        row.setProjectTemplateId(401L); row.setProjectTemplateRevisionId(702L);
        row.setFrozenBy(9L); row.setFrozenAt(LocalDateTime.of(2026,9,16,0,0));
        row.setExecutionSnapshot(JsonUtils.toJsonString(new RequirementAnalysisExecutionAccess.Frozen(binding, null, null)));
        when(mapper.selectRevision(any())).thenReturn(row);
        when(mapper.lockRevision(any())).thenReturn(row);
        when(mapper.selectEffective(any())).thenReturn(row);
        when(queries.revision(any(),any())).thenAnswer(invocation -> view("original"));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void readsTypedRevisionWithoutAFormInstanceAndPreservesNull() {
        var fact = api.inspect(query);
        assertEquals(500L, fact.entityId()); assertEquals(501L, fact.revisionId());
        assertEquals("original", fact.values().get("projectBackground"));
        assertTrue(fact.values().containsKey("networkTopology"));
        assertNull(fact.values().get("networkTopology")); assertNull(fact.form());
        assertEquals(fact, api.lockAndRevalidate(fact));
        verify(files).lockForFreeze(eq(row.revisionRef()),any());
        verify(mapper).lockCurrent(argThat(q -> q.entityId().equals(500L)));
        verify(bindings, never()).inspect(any());
    }
    @Test void cannotUseARevisionFromAnotherEntityOrAnUnfrozenDraft() {
        assertThrows(RuntimeException.class, () -> api.inspect(new RequirementAnalysisEntityFactApi.Query(100L, 999L, 501L)));
        row.setRevisionState("DRAFT");
        assertThrows(RuntimeException.class, () -> api.inspect(query));
        verifyNoInteractions(queries, files);
    }
    @Test void revalidationDetectsChangedContentAndEffectiveSelection() {
        var fact = api.inspect(query);
        when(queries.revision(any(),any())).thenReturn(view("changed"));
        assertThrows(RuntimeException.class, () -> api.lockAndRevalidate(fact));
        when(queries.revision(any(),any())).thenReturn(view("original"));
        when(mapper.selectEffective(any())).thenReturn(null);
        assertThrows(RuntimeException.class, () -> api.lockAndRevalidate(fact));
    }
    @Test void revokedPermissionDoesNotReadBusinessData() {
        when(permissions.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> api.inspect(query));
        verifyNoInteractions(mapper, queries, bindings, files);
    }
    @Test void missingOriginIsNotReplacedWithProjectDefault() {
        row.setExecutionSnapshot(null);
        assertThrows(RuntimeException.class, () -> api.inspect(query));
        verifyNoInteractions(bindings, queries);
    }

    private RequirementAnalysisEntityQueryService.View view(String content) {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("projectBackground", content); values.put("networkTopology", null);
        return new RequirementAnalysisEntityQueryService.View(100L, row.revisionMetadata(), 1, null, null, 0,
                values, List.of(), List.of(), 401L, 702L, RequirementAnalysisEntityProvider.FIELDS.fields());
    }
    private ProjectWorkBindingFact binding() {
        var target = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
        return new ProjectWorkBindingFact(100L, 3, 201L, 4, 301L, 5, 401L, 6,
                target.workBindingTypeCode(), target.targetContextCode(), target.targetObjectType(), target.targetObjectKey(),
                null, null, null, null, 702L, 2, "{}", 16L, 17L, 1, 2);
    }
}
