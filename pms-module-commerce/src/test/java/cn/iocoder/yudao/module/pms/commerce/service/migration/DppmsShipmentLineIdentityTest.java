package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DppmsShipmentLineIdentityTest {
    final DppmsCommerceEntityMapper mapper=mock(DppmsCommerceEntityMapper.class);
    final DppmsCommerceEntityResolveService service=new DppmsCommerceEntityResolveService(mapper);
    final Command command=new Command(1L,7L,100L,"CONTRACT",List.of(new Order("10","D365","01","0","ORDER")),
            List.of(new Line("20","D365","01","0","ORDER","1")),List.of(),List.of(),null);
    final ShipmentLine input=new ShipmentLine("20",3L,"D365","01","ORDER","1","10625024");
    SalesOrderLineDO row;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(mapper.selectContractIds(any())).thenReturn(List.of(1L));
        when(mapper.selectAssociatedOrderIds(any())).thenReturn(List.of(2L));
        when(mapper.selectAssociatedLineIds(any())).thenReturn(List.of(3L));
        row=new SalesOrderLineDO();row.setId(3L);row.setTenantId(1L);row.setDeleted(false);
        row.setCompanyCode("001");row.setSourceSystem("D365");row.setOrderNo("ORDER");row.setLineNo("1");
        row.setCustomInfo("{\"inventTransId\":\"10625024\"}");
        when(mapper.selectShipmentLineForUpdate(any())).thenReturn(row);
    }
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void explicitConvertedCompanyAndInventoryTransactionAreBothVerifiedUnderLock() {
        assertEquals(List.of(input),service.lockShipmentLines(command,List.of(input)));
        verify(mapper).selectShipmentLineForUpdate(argThat(q->q.tenantId()==1L && q.id()==3L));
        assertEquals("01",input.companyCode());assertEquals("001",row.getCompanyCode());
    }
    @Test void changedInventoryTransactionCannotUseTheSameOrderNumberAsProof() {
        row.setCustomInfo("{\"inventTransId\":\"10625025\"}");
        assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input)));
    }
    @Test void differentCompanyOrErpSourceIsNotAValidAssociation() {
        row.setCompanyCode("002");assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input)));
        row.setCompanyCode("001");row.setSourceSystem("SAP");
        assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input)));
    }
    @Test void aTargetIdNotResolvedFromTheProjectContractIsRejectedBeforeItIsLocked() {
        when(mapper.selectAssociatedLineIds(any())).thenReturn(List.of(4L));
        assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input)));
        verify(mapper,never()).selectShipmentLineForUpdate(any());
    }
    @Test void tenantDeletedAndAmbiguousIdentitiesRemainBlocked() {
        row.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input)));
        row.setTenantId(1L);row.setDeleted(true);assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input)));
        row.setDeleted(false);when(mapper.selectAssociatedLineIds(any())).thenReturn(List.of(3L,4L));
        assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input)));
    }
    @Test void foreignTenantCannotReachAnyOwnerTable() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input)));verifyNoInteractions(mapper);
    }
    @Test void duplicateAndEmptySelectionsDoNotProvideAValidProof() {
        assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of()));
        assertThrows(IllegalArgumentException.class,()->service.lockShipmentLines(command,List.of(input,input)));
    }
}
