package cn.iocoder.yudao.module.pms.asset.service.device;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.ProjectShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.ProjectShipmentPageQuery;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectShipmentLogisticsApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectShipmentQueryServiceTest {
    final ProjectShipmentMapper mapper=mock(ProjectShipmentMapper.class);
    final ProjectScopeApi scope=mock(ProjectScopeApi.class);
    final ProjectContractQueryApi contracts=mock(ProjectContractQueryApi.class);
    final ProjectShipmentLogisticsApi logistics=mock(ProjectShipmentLogisticsApi.class);
    final ProjectShipmentQueryService service=new ProjectShipmentQueryService(mapper,scope,contracts,logistics);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void close(){TenantContextHolder.clear();}
    ProjectShipmentQueryService.Query query(){return new ProjectShipmentQueryService.Query(1L,7L,86648L,"SN",2,20);}
    void visible(){when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(86648L,1L,Set.of(86648L),Set.of()));}
    @Test void authorizedProjectUsesOnlyItsContracts(){
        visible();when(contracts.getCurrentContractNumbers(86648L)).thenReturn(Set.of("C-1"));
        service.page(query());verify(mapper).count(new ProjectShipmentPageQuery(1L,Set.of("C-1"),"SN",20,20));
    }
    @Test void placeholderCannotReadContractFacts(){
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(86648L,1L,Set.of(),Set.of(86648L)));
        assertThrows(AccessDeniedException.class,()->service.page(query()));verifyNoInteractions(contracts,mapper);
    }
    @Test void emptyContractScopeNeverBroadens(){
        visible();when(contracts.getCurrentContractNumbers(86648L)).thenReturn(Set.of());
        assertEquals(0L,service.page(query()).getTotal());verifyNoInteractions(mapper);
    }
    @Test void crossTenantAndOversizedPageAreRejected(){
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.page(query()));
        TenantContextHolder.setTenantId(1L);
        assertThrows(IllegalArgumentException.class,()->service.page(new ProjectShipmentQueryService.Query(1L,7L,86648L,null,1,101)));
        verifyNoInteractions(mapper,contracts,scope);
    }
    @Test void logisticsAreBatchedWithoutMergingRepeatedShipmentsOrDroppingMissingPackages(){
        visible(); when(contracts.getCurrentContractNumbers(86648L)).thenReturn(Set.of("C-1"));
        var first=new cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO();
        first.setId(1L);first.setDeviceSn("SN");first.setSourceSystem("DPPMS");first.setContractNo("C-1");first.setPackageNo("P-1");
        var second=new cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO();
        second.setId(2L);second.setDeviceSn("SN");second.setSourceSystem("DPPMS");second.setContractNo("C-1");second.setPackageNo("P-1");
        var missing=new cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO();
        missing.setId(3L);missing.setDeviceSn("SN");missing.setSourceSystem("DPPMS");missing.setContractNo("C-1");
        when(mapper.count(any())).thenReturn(3L);when(mapper.selectPage(any())).thenReturn(List.of(first,second,missing));
        var fact=new ProjectShipmentLogisticsApi.Fact("DPPMS","P-1","C-1","Carrier","EXP-1","Receiver");
        var expected=new ProjectShipmentLogisticsApi.Query(1L,7L,86648L,List.of(fact.reference()));
        when(logistics.read(expected)).thenReturn(List.of(fact));
        var page=service.page(query());assertEquals(3L,page.getTotal());
        assertEquals(List.of(1L,2L,3L),page.getList().stream().map(item->item.shipment().getId()).toList());
        assertEquals(fact,page.getList().get(0).logistics());assertEquals(fact,page.getList().get(1).logistics());
        assertNull(page.getList().get(2).logistics());verify(logistics).read(expected);
    }
}
