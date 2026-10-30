package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ExactContractProjectQuery;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectContractQueryApiImplTest {
    @Test void exactLookupUsesTrustedTenantAndUnmodifiedLiteralAndRejectsEmptyInput() {
        var mapper=mock(ProjectCommerceMapper.class);
        var api=new ProjectContractQueryApiImpl(mapper);
        assertThrows(IllegalArgumentException.class,()->api.findProjectIdsByExactContractNo(null));
        assertThrows(IllegalArgumentException.class,()->api.findProjectIdsByExactContractNo(new ProjectContractQueryApi.ExactContractQuery(" ")));
        assertThrows(IllegalArgumentException.class,()->api.findProjectIdsByExactContractNo(new ProjectContractQueryApi.ExactContractQuery("x".repeat(513))));
        verifyNoInteractions(mapper);
        TenantContextHolder.setTenantId(7L);
        try {
            var query=new ExactContractProjectQuery(7L," C%_ ");
            when(mapper.selectProjectIdsByExactContractNo(query)).thenReturn(List.of(42L,42L));
            assertEquals(Set.of(42L),api.findProjectIdsByExactContractNo(new ProjectContractQueryApi.ExactContractQuery(" C%_ ")));
            verify(mapper).selectProjectIdsByExactContractNo(query);
        } finally { TenantContextHolder.clear(); }
    }
}
