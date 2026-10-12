package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DppmsCommerceEntityResolveServiceTest {
    DppmsCommerceEntityMapper mapper=mock(DppmsCommerceEntityMapper.class);
    DppmsCommerceEntityResolveService service=new DppmsCommerceEntityResolveService(mapper);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    Command command(String orderCompany,String lineCompany) {
        return new Command(1L,1L,100L,"TEST",
                List.of(new Order("10","SAP",orderCompany,"0","ORDER")),
                List.of(new Line("20","SAP",lineCompany,"0","ORDER","1")),List.of(),List.of(),"30");
    }
    @Test void refusesDetachedContractBeforeResolvingAnyOrder() {
        assertThrows(IllegalArgumentException.class,()->service.resolve(command("01","01")));
        verify(mapper,never()).selectAssociatedOrderIds(any());verify(mapper,never()).selectAssociatedLineIds(any());
    }
    @Test void ambiguousOrdersAreNotAssignedToOneArbitraryTarget() {
        when(mapper.selectContractIds(any())).thenReturn(List.of(1L));
        when(mapper.selectAssociatedOrderIds(any())).thenReturn(List.of(2L,3L));
        var results=service.resolve(command("01","01"));
        assertEquals("NATIVE_SOURCE_IDENTITY_AMBIGUOUS",results.get(1).reasonCode());
        assertEquals("NATIVE_SOURCE_IDENTITY_UNRESOLVED",results.get(2).reasonCode());
        verify(mapper,never()).selectAssociatedLineIds(any());
    }
    @Test void convertsCompanyBeforeExactOrderResolutionAndKeepsOriginalInput() {
        when(mapper.selectContractIds(any())).thenReturn(List.of(1L));
        when(mapper.selectAssociatedOrderIds(any())).thenReturn(List.of(2L));
        when(mapper.selectAssociatedLineIds(any())).thenReturn(List.of(3L));
        var source=command("02","02");
        var results=service.resolve(source);
        verify(mapper).selectAssociatedOrderIds(argThat(q->"002".equals(q.companyCode())&&"02".equals(q.rawCompanyCode())));
        verify(mapper).selectAssociatedLineIds(argThat(q->q.orderId()==2L));
        assertEquals("02",source.orders().getFirst().companyCode());
        assertEquals("02",source.lines().getFirst().companyCode());
        assertEquals(2L,results.get(1).targets().getFirst().targetId());
        assertEquals(3L,results.get(2).targets().getFirst().targetId());
    }
    @Test void differentCompaniesCannotShareOneParentOrder() {
        when(mapper.selectContractIds(any())).thenReturn(List.of(1L));
        when(mapper.selectAssociatedOrderIds(any())).thenReturn(List.of(2L));
        var results=service.resolve(command("01","02"));
        assertEquals("NATIVE_SOURCE_IDENTITY_UNRESOLVED",results.get(2).reasonCode());
        verify(mapper,never()).selectAssociatedLineIds(any());
    }
    @Test void salesAndReturnWithTheSameNumberResolveTheirOwnTypedParentAndLine() {
        when(mapper.selectContractIds(any())).thenReturn(List.of(1L));
        when(mapper.selectAssociatedOrderIds(any())).thenAnswer(call->{
            var query=call.getArgument(0,cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.AssociatedOrderQuery.class);
            return List.of("0".equals(query.type())?2L:3L);
        });
        when(mapper.selectAssociatedLineIds(any())).thenAnswer(call->{
            var query=call.getArgument(0,cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsCommerceEntityMapper.AssociatedLineQuery.class);
            assertEquals("1",query.lineNo());
            return List.of(query.orderId()==2L?21L:31L);
        });
        var source=new Command(1L,1L,100L,"TEST",
                List.of(new Order("return","SAP","02","1","SAME"),new Order("sales","SAP","02","0","SAME")),
                List.of(new Line("sales-line","SAP","02","0","SAME","1"),new Line("return-line","SAP","02","1","SAME","1")),
                List.of(),List.of(),"contract");
        var results=service.resolve(source);
        assertEquals(21L,results.get(3).targets().getFirst().targetId());
        assertEquals(31L,results.get(4).targets().getFirst().targetId());
        assertNull(results.get(3).reasonCode());assertNull(results.get(4).reasonCode());
        verify(mapper).selectAssociatedLineIds(argThat(query->query.orderId()==2L));
        verify(mapper).selectAssociatedLineIds(argThat(query->query.orderId()==3L));
    }
    @Test void mismatchedOrMissingLineTypeCannotFallBackToAnotherOrderType() {
        when(mapper.selectContractIds(any())).thenReturn(List.of(1L));
        when(mapper.selectAssociatedOrderIds(any())).thenReturn(List.of(2L));
        for(String type:new String[]{"1",null}) {
            var source=new Command(1L,1L,100L,"TEST",List.of(new Order("sales","SAP","01","0","SAME")),
                    List.of(new Line("line","SAP","01",type,"SAME","1")),List.of(),List.of(),"contract");
            var results=service.resolve(source);
            assertEquals("NATIVE_SOURCE_IDENTITY_UNRESOLVED",results.get(2).reasonCode());assertTrue(results.get(2).targets().isEmpty());
        }
        verify(mapper,never()).selectAssociatedLineIds(any());
    }
    @Test void unknownCompanyBlocksBeforeAnyNativeResolution() {
        for (var source:List.of(command("05","01"),command("01","05"),command("001","01"))) {
            var failure=assertThrows(IllegalArgumentException.class,()->service.resolve(source));
            assertTrue(failure.getMessage().contains("DPPMS_COMPANY_CODE_UNMAPPED"));
        }
        verifyNoInteractions(mapper);
    }
}
