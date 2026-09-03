package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopePageQuery;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
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
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setId(101L);
        scope.setProjectId(10L);
        when(scopeMapper.selectPageByProjectScope(any())).thenReturn(List.of(scope));
        DeliveryScopeDetailDO detail = new DeliveryScopeDetailDO();
        detail.setDeliveryScopeId(101L);
        when(detailMapper.selectByScopeIds(any())).thenReturn(List.of(detail));

        var result = service.page(1L, 7L, 10L, null, false, 0, 20);

        assertEquals(3L, result.getTotal());
        assertEquals(List.of(detail), result.getList().getFirst().details());
        ArgumentCaptor<DeliveryScopePageQuery> query = ArgumentCaptor.forClass(DeliveryScopePageQuery.class);
        verify(scopeMapper).selectCountByProjectScope(query.capture());
        assertEquals(List.of(10L, 20L), query.getValue().projectIds());
        assertFalse(query.getValue().includeHistory());
    }

    @Test
    void shouldReturnEmptyWithoutDataReadsWhenRequestedProjectIsInvisible() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(Set.of(20L));

        assertTrue(service.page(1L, 7L, 10L, null, false, 0, 20).getList().isEmpty());

        verifyNoInteractions(scopeMapper, detailMapper);
    }

    @Test
    void shouldFailClosedWhenProjectOwnerIsUnavailable() {
        when(projectScopeApi.resolveAllCurrent(any())).thenThrow(new IllegalStateException("down"));

        assertEquals(0L, service.page(1L, 7L, null, null, true, 0, 20).getTotal());

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
}
