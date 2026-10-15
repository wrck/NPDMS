package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsLocalContractSourceApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsLocalContractSourceMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsLocalContractSourceServiceTest {
    final DppmsLocalContractSourceMapper mapper=mock(DppmsLocalContractSourceMapper.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final AdminUserApi users=mock(AdminUserApi.class);
    final DppmsLocalContractSourceService service=new DppmsLocalContractSourceService(mapper,permissions,users);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);}
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void allPagesAreReadUsingCompanyAndContractBoundaryWithConflictEvidencePreserved() {
        var first=new Contract("A","C1","C00001",null,null);
        var second=new Contract("B","C1",null,null,"CONTRACT_CUSTOMER_CONFLICT");
        when(mapper.selectPage(new DppmsLocalContractSourceMapper.Page(1L,List.of("C1"),null,null))).thenReturn(List.of(first));
        when(mapper.selectPage(new DppmsLocalContractSourceMapper.Page(1L,List.of("C1"),"A","C1"))).thenReturn(List.of(second));
        when(mapper.selectPage(new DppmsLocalContractSourceMapper.Page(1L,List.of("C1"),"B","C1"))).thenReturn(List.of());
        assertEquals(List.of(first,second),service.read(new Query(1L,2L,List.of("C1"),1024)));
        verify(mapper,times(3)).selectPage(any());
    }
    @Test void oversizedSnapshotFailsInsteadOfReturningPartialContracts() {
        when(mapper.selectPage(any())).thenReturn(List.of(new Contract("A","C1","C00001",null,null)));
        assertThrows(IllegalArgumentException.class,()->service.read(new Query(1L,2L,List.of(),1)));
        verify(mapper,times(1)).selectPage(any());
    }
    @Test void tenantAndPermissionFailuresPreventSourceRead() {
        assertThrows(IllegalArgumentException.class,()->service.read(new Query(2L,2L,List.of(),1024)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.read(new Query(1L,3L,List.of(),1024)));
        verifyNoInteractions(mapper);
    }
}
