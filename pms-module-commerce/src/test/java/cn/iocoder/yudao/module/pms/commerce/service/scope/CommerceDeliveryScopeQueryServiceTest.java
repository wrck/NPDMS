package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopePageQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeOrderFieldsRow;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopePageRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommerceDeliveryScopeQueryServiceTest {

    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private DeliveryScopeMapper scopeMapper;
    @Mock private DeliveryScopeDetailMapper detailMapper;
    @Mock private DeliveryScopeProjectVersionService projectVersionService;
    private CommerceDeliveryScopeQueryService service;

    @BeforeEach
    void setUp() {
        service = new CommerceDeliveryScopeQueryService(
                projectScopeApi, scopeMapper, detailMapper, projectVersionService);
    }

    @Test
    void shouldPageOnlyStableCurrentProjectScopeAndAttachDetails() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(20L, 10L));
        when(scopeMapper.selectCountByProjectScope(any())).thenReturn(3L);
        DeliveryScopePageRow scope = new DeliveryScopePageRow();
        scope.setId(101L);
        scope.setProjectId(10L);
        when(scopeMapper.selectPageByProjectScope(any())).thenReturn(List.of(scope));
        when(scopeMapper.selectOrderFields(any())).thenReturn(List.of(
                new DeliveryScopeOrderFieldsRow(101L,"C1",new java.math.BigDecimal("4"),null)));
        DeliveryScopeDetailDO detail = new DeliveryScopeDetailDO();
        detail.setDeliveryScopeId(101L);
        when(detailMapper.selectByScopeIds(any())).thenReturn(List.of(detail));

        var result = service.page(CommerceDeliveryScopeQueryService.PageQuery.builder()
                .tenantId(1L).subjectUserId(7L).projectId(10L).limit(20).build());

        assertEquals(3L, result.getTotal());
        assertEquals(List.of(detail), result.getList().getFirst().details());
        assertEquals("C1",result.getList().getFirst().orderFields().contractNo());
        assertEquals(new java.math.BigDecimal("4"),result.getList().getFirst().orderFields().orderQuantity());
        assertNull(result.getList().getFirst().orderFields().unitCode());
        verify(scopeMapper).selectOrderFields(argThat(orderQuery->orderQuery.tenantId()==1L && orderQuery.scopeIds().equals(List.of(101L))));
        assertEquals("C1",result.getList().getFirst().contractNo());
        ArgumentCaptor<DeliveryScopePageQuery> query = ArgumentCaptor.forClass(DeliveryScopePageQuery.class);
        verify(scopeMapper).selectCountByProjectScope(query.capture());
        assertEquals(List.of(10L), query.getValue().projectIds());
        assertFalse(query.getValue().includeHistory());
    }

    @Test
    void unfilteredPageRetainsAllAuthorizedProjects() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(20L, 10L));
        service.page(CommerceDeliveryScopeQueryService.PageQuery.builder()
                .tenantId(1L).subjectUserId(7L).limit(20).build());
        verify(scopeMapper).selectCountByProjectScope(argThat(query ->
                query.projectId() == null && query.projectIds().equals(List.of(10L, 20L))));
        verifyNoInteractions(detailMapper);
    }

    @Test
    void shouldReturnEmptyWithoutDataReadsWhenRequestedProjectIsInvisible() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(20L));

        assertTrue(service.page(CommerceDeliveryScopeQueryService.PageQuery.builder()
                .tenantId(1L).subjectUserId(7L).projectId(10L).limit(20).build()).getList().isEmpty());

        verifyNoInteractions(scopeMapper, detailMapper);
    }

    @Test void missingOrderContractDoesNotHideAuthorizedDeliveryScopesOrInventAProjectContract() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(10L));
        when(scopeMapper.selectCountByProjectScope(any())).thenReturn(1L);
        var scope = new DeliveryScopePageRow(); scope.setId(101L); scope.setProjectId(10L);
        when(scopeMapper.selectPageByProjectScope(any())).thenReturn(List.of(scope));
        when(scopeMapper.selectOrderFields(any())).thenReturn(List.of());
        var result = service.page(CommerceDeliveryScopeQueryService.PageQuery.builder().tenantId(1L).subjectUserId(7L).projectId(10L).limit(20).build());
        assertEquals(1, result.getList().size());
        assertNull(result.getList().getFirst().contractNo());
    }

    @Test
    void shouldFailClosedWhenProjectOwnerIsUnavailable() {
        when(projectScopeApi.resolveAllCurrent(any())).thenThrow(new IllegalStateException("down"));

        assertEquals(0L, service.page(CommerceDeliveryScopeQueryService.PageQuery.builder()
                .tenantId(1L).subjectUserId(7L).includeHistory(true).limit(20).build()).getTotal());

        verifyNoInteractions(scopeMapper, detailMapper);
    }

    @Test
    void shouldExposeCurrentDeliveryScopeVersionOnlyForVisibleProject() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(10L));
        when(projectVersionService.current(1L, 10L)).thenReturn(6L);

        assertEquals(6L, service.currentVersion(1L, 7L, 10L));
        assertEquals(DeliveryScopeFactException.Code.PROJECT_NOT_VISIBLE_OR_INELIGIBLE,
                assertThrows(DeliveryScopeFactException.class,
                        () -> service.currentVersion(1L, 7L, 11L)).getCode());
        verify(projectVersionService).current(1L, 10L);
    }

    @Test
    void retainsEachAuthorizedSourceProjectionWithoutMutatingOrRegroupingScopes() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(84499L));
        when(scopeMapper.selectCountByProjectScope(any())).thenReturn(3L);
        var first = productScope(101L, " 02011299 ");
        var duplicate = productScope(102L, "02011299");
        var missing = productScope(103L, "02010257");
        first.setProductModel("QSFP28-LX2-SM1310-CWDM4");
        first.setProductName("产品名称");
        duplicate.setProductModel(first.getProductModel());
        when(scopeMapper.selectPageByProjectScope(any())).thenReturn(List.of(first, duplicate, missing));

        var result = service.page(1L, 7L, 84499L, null, false, 0, 20);

        assertEquals("QSFP28-LX2-SM1310-CWDM4", result.getList().getFirst().scope().getProductModel());
        assertEquals("产品名称", result.getList().getFirst().scope().getProductName());
        assertEquals(result.getList().getFirst().scope().getProductModel(), result.getList().get(1).scope().getProductModel());
        assertNull(result.getList().getLast().scope().getProductModel());
        assertSame(first, result.getList().getFirst().scope());
        assertEquals(" 02011299 ", first.getProductCode());
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
    }

    @Test
    void sourceQueryFailureIsPropagatedInsteadOfReturningAnEmptyOrPartialPage() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(84499L));
        when(scopeMapper.selectCountByProjectScope(any())).thenReturn(1L);
        when(scopeMapper.selectPageByProjectScope(any())).thenThrow(new IllegalStateException("product source unavailable"));
        assertThrows(IllegalStateException.class, () -> service.page(1L, 7L, 84499L, null, false, 0, 20));
        verifyNoInteractions(detailMapper);
    }

    private DeliveryScopePageRow productScope(Long id, String code) {
        var scope = new DeliveryScopePageRow();
        scope.setId(id);
        scope.setProjectId(84499L);
        scope.setProductCode(code);
        return scope;
    }
}
