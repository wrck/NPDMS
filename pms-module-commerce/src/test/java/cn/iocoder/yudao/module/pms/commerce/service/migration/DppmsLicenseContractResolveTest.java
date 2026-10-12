package cn.iocoder.yudao.module.pms.commerce.service.migration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DppmsLicenseContractResolveTest {
    final DppmsCommerceEntityMapper mapper=mock(DppmsCommerceEntityMapper.class);
    final DppmsCommerceEntityResolveService service=new DppmsCommerceEntityResolveService(mapper);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void onlyOneExactProjectContractMayBecomeAnAssociation() {
        when(mapper.selectContractIds(any())).thenReturn(List.of(10L),List.of(11L,12L),List.of());
        var result=service.resolveLicenseContracts(new LicenseContracts(1L,7L,100L,List.of("ONE","TWO","NONE")));
        assertEquals(10L,result.get(0).contractId());assertNull(result.get(1).contractId());assertNull(result.get(2).contractId());
        verify(mapper).selectContractIds(argThat(q->q.tenantId()==1L && q.projectId()==100L && q.contractNo().equals("ONE")));
    }
    @Test void emptySelectionCannotExpandToAllContracts() {
        assertTrue(service.resolveLicenseContracts(new LicenseContracts(1L,7L,100L,List.of())).isEmpty());
        verifyNoInteractions(mapper);
    }
    @Test void wrongTenantAndMissingActorFailBeforeLookup() {
        assertThrows(IllegalArgumentException.class,()->service.resolveLicenseContracts(new LicenseContracts(2L,7L,100L,List.of("ONE"))));
        assertThrows(IllegalArgumentException.class,()->service.resolveLicenseContracts(new LicenseContracts(1L,null,100L,List.of("ONE"))));
        verifyNoInteractions(mapper);
    }
}
