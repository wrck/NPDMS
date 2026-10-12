package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsExecutionOrganizationReferencesTest {
    final ManagedOrganizationApi api=mock(ManagedOrganizationApi.class);
    final DppmsExecutionOrganizationReferences references=new DppmsExecutionOrganizationReferences(api);

    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void cleanup(){TenantContextHolder.clear();}

    @Test void exactAndAdjudicatedAliasCanReferenceDisabledHistoryWithoutChangingNamesOrGrants() {
        var company=new CompanyDO();company.setId(71L);company.setTenantId(1L);
        company.setCode("001");company.setName("正式公司");company.setStatus(1);
        var dept=new DeptDO();dept.setId(81L);dept.setTenantId(1L);dept.setCode("162102");dept.setName("正式部门");dept.setStatus(1);
        when(api.list()).thenReturn(List.of(new ManagedOrganizationApi.Node(company,null,null),new ManagedOrganizationApi.Node(null,dept,null)));
        var row=new CrmExecutionOrderDO();row.setCompanyCode("001");row.setDepartmentCode("311202");row.setDepartmentName("原办事处名称");
        references.load().associate(row);
        assertEquals(71L,row.getCompanyId());assertEquals(81L,row.getDepartmentId());
        assertEquals("162102",row.getDepartmentCode());assertEquals("原办事处名称",row.getDepartmentName());
        assertEquals(1,company.getStatus());assertEquals(1,dept.getStatus());
        verify(api).list();verifyNoMoreInteractions(api);
    }

    @Test void unapprovedAliasAndSharedPrefixCannotUseAParentDepartment() {
        var dept=new DeptDO();dept.setId(81L);dept.setTenantId(1L);dept.setCode("310000");
        when(api.list()).thenReturn(List.of(new ManagedOrganizationApi.Node(null,dept,null)));
        for(String code:List.of("311201","311211","310000001")) {
            var row=new CrmExecutionOrderDO();row.setDepartmentCode(code);
            assertThrows(IllegalArgumentException.class,()->references.load().associate(row));
            assertNull(row.getDepartmentId());
        }
    }

    @Test void crossTenantOrAmbiguousDirectoryCannotCreateReferences() {
        var company=new CompanyDO();company.setId(71L);company.setTenantId(2L);company.setCode("001");
        var node=new ManagedOrganizationApi.Node(company,null,null);
        when(api.list()).thenReturn(List.of(node));
        assertThrows(IllegalStateException.class,references::load);
        company.setTenantId(1L);when(api.list()).thenReturn(List.of(node,node));
        assertThrows(IllegalStateException.class,references::load);
    }
}
