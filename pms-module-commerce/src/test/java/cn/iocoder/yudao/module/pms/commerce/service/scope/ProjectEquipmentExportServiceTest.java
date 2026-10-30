package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.ProjectEquipmentExportMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.*;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectEquipmentExportServiceTest {
    final ProjectEquipmentExportMapper mapper=mock(ProjectEquipmentExportMapper.class);
    final ProjectScopeApi projects=mock(ProjectScopeApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final ContractAccessService contracts=mock(ContractAccessService.class);
    final ProjectEquipmentExportService service=new ProjectEquipmentExportService(mapper,projects,permissions,contracts);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var user=new LoginUser();user.setId(7L);user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));
        when(permissions.hasAnyPermissions(7L,"pms:device:query")).thenReturn(true);
        when(permissions.hasAnyPermissions(7L,"pms:project-device:export")).thenReturn(true);
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(contracts.orderLineReadScope(any())).thenReturn(new ContractAccessService.AccessScope(List.of("001"),List.of(10L)));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear();SecurityContextHolder.clearContext(); }
    private ProjectEquipmentExportSource source(long scope,long line,String code,BigDecimal delivered) {
        return new ProjectEquipmentExportSource(scope,line,code,"产品"+code,"MODEL","CRM产品","001ORDER",BigDecimal.ONE,line,code,delivered,BigDecimal.ZERO);
    }
    @Test void independentPermissionAndProjectScopeAreRequiredBeforeReading() {
        when(permissions.hasAnyPermissions(7L,"pms:project-device:export")).thenReturn(false);
        assertThrows(ServiceException.class,()->service.read(10L)); verifyNoInteractions(mapper,projects);
        when(permissions.hasAnyPermissions(7L,"pms:project-device:export")).thenReturn(true);
        when(projects.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of(10L)));
        assertThrows(ServiceException.class,()->service.read(10L)); verifyNoInteractions(mapper);
    }
    @Test void duplicateAllocationsDoNotDoubleCountOrderQuantityAndUnknownIsBlank() {
        when(mapper.selectBatch(any())).thenReturn(List.of(source(1,11,"02010005",BigDecimal.TEN),source(2,11,"02010005",BigDecimal.TEN),source(3,12,"02010075",null)));
        var result=service.read(10L);
        assertEquals(2,result.size());
        assertEquals(new BigDecimal("2"),result.getFirst().getProjectQty());
        assertEquals(BigDecimal.TEN,result.getFirst().getDeliveredQty());
        assertEquals("02010005",result.getFirst().getProductCode());assertEquals("MODEL",result.getFirst().getProductModel());
        assertNull(result.getLast().getDeliveredQty());assertEquals(BigDecimal.ZERO,result.getLast().getOpenQty());
    }
    @Test void moreThanOneBatchIsExportedAndReferenceFailureAborts() {
        var first=java.util.stream.LongStream.rangeClosed(1,500).mapToObj(id->source(id,id,"P"+id,BigDecimal.ONE)).toList();
        when(mapper.selectBatch(any())).thenAnswer(call->{ProjectEquipmentExportQuery q=call.getArgument(0);assertEquals(1L,q.tenantId());assertEquals(10L,q.projectId());
            if(q.afterId()==null)return first;assertEquals(500L,q.afterId());return List.of(source(501,501,"LAST",BigDecimal.ONE));});
        assertEquals(501,service.read(10L).size());
        doThrow(new IllegalStateException("product source unavailable")).when(mapper).selectBatch(any());
        assertThrows(IllegalStateException.class,()->service.read(10L));
    }
    @Test void mismatchedTenantIsDeniedBeforeQuerying() {
        TenantContextHolder.setTenantId(2L);assertThrows(ServiceException.class,()->service.read(10L));verifyNoInteractions(mapper,projects);
    }
    @Test void missingOrMismatchedOrderLineDoesNotBecomeZero() {
        when(mapper.selectBatch(any())).thenReturn(List.of(new ProjectEquipmentExportSource(1L,11L,"P","产品",null,null,"ORDER",BigDecimal.ONE,null,null,null,null)));
        var row=service.read(10L).getFirst();assertNull(row.getDeliveredQty());assertNull(row.getOpenQty());assertEquals(BigDecimal.ONE,row.getProjectQty());
    }

    @Test void whitespaceProductCodeCannotMergeAnUnknownModelIntoTheCanonicalProduct() {
        var padded=new ProjectEquipmentExportSource(1L,11L," A ","有空白编码",null,null,"SO-A",BigDecimal.ONE,11L," A ",BigDecimal.ONE,BigDecimal.ZERO);
        when(mapper.selectBatch(any())).thenReturn(List.of(padded,source(2,12,"A",BigDecimal.ONE)));
        var rows=service.read(10L);
        assertEquals(List.of(" A ","A"),rows.stream().map(ProjectEquipmentExportRow::getProductCode).toList());
        assertNull(rows.getFirst().getProductModel());assertEquals("MODEL",rows.getLast().getProductModel());
        assertTrue(rows.stream().allMatch(row->BigDecimal.ONE.equals(row.getProjectQty())));
    }
}
