package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardProviderApi;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
import cn.iocoder.yudao.module.pms.project.config.ProjectGovernanceGuardProperties;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_GUARD_TOKEN_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardService.GovernanceAction.EXCEPTION_CLOSE;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardService.GovernanceAction.ROLLBACK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectGovernanceGuardServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long PROJECT_ID = 11L;
    private static final Long ROOT_ID = 10L;
    private static final Integer PROJECT_VERSION = 3;

    private ProjectMasterMapper projectMapper;
    private ProjectTreeVersionMapper treeVersionMapper;
    private ProjectTreePathMapper pathMapper;
    private ProjectGovernanceProviderRegistry registry;
    private ProjectGovernanceGuardTokenService tokenService;
    private ProjectGovernanceGuardService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        projectMapper = mock(ProjectMasterMapper.class);
        treeVersionMapper = mock(ProjectTreeVersionMapper.class);
        pathMapper = mock(ProjectTreePathMapper.class);
        registry = mock(ProjectGovernanceProviderRegistry.class);
        ProjectGovernanceGuardProperties properties = new ProjectGovernanceGuardProperties();
        properties.setSigningKey("task-6-test-signing-key-with-sufficient-entropy");
        tokenService = new ProjectGovernanceGuardTokenService(properties);
        service = new ProjectGovernanceGuardService(
                projectMapper, treeVersionMapper, pathMapper, registry, tokenService);
        stubTree(7L);
        when(registry.inspectAll(any())).thenReturn(providerFacts("w1"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldEvaluateCompleteTreeAndIssueOpaqueToken() {
        ProjectGovernanceGuardResult result = service.evaluate(PROJECT_ID, EXCEPTION_CLOSE, actor());

        assertTrue(result.allowed());
        assertNotNull(result.guardToken());
        assertEquals(ROOT_ID, result.treeRootProjectId());
        assertEquals(7L, result.treeVersion());
        assertEquals(6, result.providerFacts().size());
        ArgumentCaptor<ProjectGovernanceGuardQuery> queryCaptor =
                ArgumentCaptor.forClass(ProjectGovernanceGuardQuery.class);
        verify(registry).inspectAll(queryCaptor.capture());
        assertEquals(Set.of(11L, 12L), queryCaptor.getValue().projectIds());
        assertEquals("EXCEPTION_CLOSE", queryCaptor.getValue().action());
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = service.evaluate(PROJECT_ID, EXCEPTION_CLOSE, actor()).guardToken();
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.verifyAndRevalidate(tampered, PROJECT_ID,
                        EXCEPTION_CLOSE, PROJECT_VERSION, actor()));

        assertEquals(PROJECT_GOVERNANCE_GUARD_TOKEN_INVALID.getCode(), error.getCode());
    }

    @Test
    void shouldRejectActionMismatchAndCrossTenantReplay() {
        String token = service.evaluate(PROJECT_ID, EXCEPTION_CLOSE, actor()).guardToken();
        ServiceException actionError = assertThrows(ServiceException.class,
                () -> service.verifyAndRevalidate(token, PROJECT_ID,
                        ROLLBACK, PROJECT_VERSION, actor()));
        assertEquals(PROJECT_GOVERNANCE_VERSION_CONFLICT.getCode(), actionError.getCode());

        TenantContextHolder.setTenantId(8L);
        ServiceException tenantError = assertThrows(ServiceException.class,
                () -> service.verifyAndRevalidate(token, PROJECT_ID,
                        EXCEPTION_CLOSE, PROJECT_VERSION,
                        new ProjectGovernanceGuardService.Actor(8L, 9L, "corr-8")));
        assertEquals(PROJECT_GOVERNANCE_VERSION_CONFLICT.getCode(), tenantError.getCode());
    }

    @Test
    void shouldRejectTreeVersionChange() {
        String token = service.evaluate(PROJECT_ID, EXCEPTION_CLOSE, actor()).guardToken();
        ProjectTreeVersionDO nextTree = tree(8L);
        when(treeVersionMapper.selectLatestActive(ROOT_ID)).thenReturn(nextTree);
        when(pathMapper.selectByAncestor(ROOT_ID, 8L, PROJECT_ID, null))
                .thenReturn(List.of(path(PROJECT_ID, 0, 8L), path(12L, 1, 8L)));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.verifyAndRevalidate(token, PROJECT_ID,
                        EXCEPTION_CLOSE, PROJECT_VERSION, actor()));

        assertEquals(PROJECT_GOVERNANCE_VERSION_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void shouldRejectProviderWatermarkChange() {
        when(registry.inspectAll(any())).thenReturn(providerFacts("w1"), providerFacts("w2"));
        String token = service.evaluate(PROJECT_ID, EXCEPTION_CLOSE, actor()).guardToken();

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.verifyAndRevalidate(token, PROJECT_ID,
                        EXCEPTION_CLOSE, PROJECT_VERSION, actor()));

        assertEquals(PROJECT_GOVERNANCE_VERSION_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void shouldRevalidateWhenEveryFrozenVersionIsUnchanged() {
        String token = service.evaluate(PROJECT_ID, EXCEPTION_CLOSE, actor()).guardToken();

        ProjectGovernanceGuardService.VerifiedGuard verified = service.verifyAndRevalidate(
                token, PROJECT_ID, EXCEPTION_CLOSE, PROJECT_VERSION, actor());

        assertEquals(PROJECT_ID, verified.claims().projectId());
        assertTrue(verified.latest().allowed());
    }

    @Test
    void registryShouldFailClosedForMissingAndTimedOutProviders() {
        List<ProjectGovernanceGuardProviderApi> providers = new ArrayList<>();
        for (String code : ProjectGovernanceProviderRegistry.REQUIRED_PROVIDERS) {
            if (!"COLLECTION".equals(code)) {
                providers.add(provider(code, false));
            }
        }
        ProjectGovernanceGuardQuery query = new ProjectGovernanceGuardQuery(
                TENANT_ID, Set.of(PROJECT_ID), "EXCEPTION_CLOSE", LocalDateTime.now());
        ProjectGovernanceProviderFact missing = new ProjectGovernanceProviderRegistry(providers)
                .inspectAll(query).stream().filter(fact -> "COLLECTION".equals(fact.provider()))
                .findFirst().orElseThrow();
        assertEquals("PROVIDER_UNAVAILABLE", missing.blockers().getFirst().code());

        providers.add(provider("COLLECTION", true));
        ProjectGovernanceProviderFact timedOut = new ProjectGovernanceProviderRegistry(providers)
                .inspectAll(query).stream().filter(fact -> "COLLECTION".equals(fact.provider()))
                .findFirst().orElseThrow();
        assertEquals("QUERY_FAILED", timedOut.watermark());
        assertFalse(timedOut.blockers().isEmpty());
    }

    private void stubTree(Long treeVersion) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setRootId(ROOT_ID);
        project.setVersion(PROJECT_VERSION);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(treeVersionMapper.selectLatestActive(ROOT_ID)).thenReturn(tree(treeVersion));
        when(pathMapper.selectByAncestor(ROOT_ID, treeVersion, PROJECT_ID, null))
                .thenReturn(List.of(path(PROJECT_ID, 0, treeVersion), path(12L, 1, treeVersion)));
    }

    private static ProjectTreeVersionDO tree(Long treeVersion) {
        ProjectTreeVersionDO tree = new ProjectTreeVersionDO();
        tree.setTenantId(TENANT_ID);
        tree.setRootProjectId(ROOT_ID);
        tree.setTreeVersion(treeVersion);
        tree.setStatus("ACTIVE");
        return tree;
    }

    private static ProjectTreePathDO path(Long descendantId, int distance, Long treeVersion) {
        ProjectTreePathDO path = new ProjectTreePathDO();
        path.setTenantId(TENANT_ID);
        path.setRootProjectId(ROOT_ID);
        path.setTreeVersion(treeVersion);
        path.setAncestorProjectId(PROJECT_ID);
        path.setDescendantProjectId(descendantId);
        path.setDistance(distance);
        return path;
    }

    private static List<ProjectGovernanceProviderFact> providerFacts(String watermarkSuffix) {
        return ProjectGovernanceProviderRegistry.REQUIRED_PROVIDERS.stream()
                .map(code -> new ProjectGovernanceProviderFact(code, code + "_V1",
                        code + "_" + watermarkSuffix, code + "_digest_" + watermarkSuffix, List.of()))
                .toList();
    }

    private static ProjectGovernanceGuardProviderApi provider(String code, boolean fail) {
        ProjectGovernanceGuardProviderApi provider = mock(ProjectGovernanceGuardProviderApi.class);
        when(provider.providerCode()).thenReturn(code);
        if (fail) {
            when(provider.inspect(any())).thenThrow(new IllegalStateException("timeout"));
        } else {
            when(provider.inspect(any())).thenReturn(new ProjectGovernanceProviderFact(
                    code, code + "_V1", "EMPTY", code + "_digest", List.of()));
        }
        return provider;
    }

    private static ProjectGovernanceGuardService.Actor actor() {
        return new ProjectGovernanceGuardService.Actor(TENANT_ID, 9L, "corr-1");
    }
}
