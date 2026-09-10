package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.NormalClosureMapper;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NormalClosureProcessViewQueryTest {
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void resolvesTenantApplicationIdentityThenUsesExistingAuthorizedDetailQuery() {
        TenantContextHolder.setTenantId(1L);
        var actor = new NormalClosureAccess.Actor(1L, 9L, "process-view");
        var access = mock(NormalClosureAccess.class);
        var mapper = mock(NormalClosureMapper.class);
        var service = new NormalClosureQueryService(access, mapper,
                mock(ProjectRuntimeGraphResolver.class), mock(PermissionApi.class));
        var identity = new NormalClosureMapper.ApplicationIdentityQuery(1L, 100L);
        var query = new NormalClosureMapper.ApplicationQuery(1L, 10L, 100L);
        var snapshotQuery = new NormalClosureMapper.SnapshotQuery(1L, 10L, 200L);
        var application = new NormalClosureApplicationDO();
        application.setId(100L); application.setProjectId(10L); application.setSnapshotId(200L);
        var snapshot = new NormalClosureSnapshotDO(); snapshot.setId(200L);
        var review = new NormalClosureReviewDO(); review.setId(300L);
        when(mapper.selectApplicationProjectId(identity)).thenReturn(10L);
        when(mapper.selectApplication(query)).thenReturn(application);
        when(mapper.selectSnapshot(snapshotQuery)).thenReturn(snapshot);
        when(mapper.selectReviews(query)).thenReturn(List.of(review));

        var result = service.processViewByApplicationId(100L, actor);

        assertEquals(10L, result.projectId());
        assertSame(application, result.application());
        assertSame(snapshot, result.snapshot());
        assertEquals(List.of(review), result.reviews());
        var order = inOrder(mapper, access);
        order.verify(mapper).selectApplicationProjectId(identity);
        order.verify(access).read(10L, actor, NormalClosureAccess.QUERY);
        order.verify(mapper).selectApplication(query);
        order.verify(mapper).selectSnapshot(snapshotQuery);
        order.verify(mapper).selectReviews(query);
        verifyNoMoreInteractions(mapper, access);
    }
}
