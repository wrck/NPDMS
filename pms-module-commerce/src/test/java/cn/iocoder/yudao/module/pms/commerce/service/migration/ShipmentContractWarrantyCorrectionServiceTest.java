package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentContractWarrantyCorrectionApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ShipmentContractWarrantyCorrectionMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ShipmentContractWarrantyCorrectionMapper.Current;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShipmentContractWarrantyCorrectionServiceTest {
    final String before="a".repeat(64), after="b".repeat(64);
    ShipmentContractWarrantyCorrectionMapper mapper=mock(ShipmentContractWarrantyCorrectionMapper.class);
    ShipmentContractWarrantyCorrectionService service=new ShipmentContractWarrantyCorrectionService(mapper);
    Request request(){return new Request(1L,"integration:1:100",List.of(new Entry(2L,"C",before,after)));}
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void previewDoesNotWriteAndOnlyApprovedBlankIsNormalized() {
        var current=new Current(2L,"  ",0,before,after);
        when(mapper.selectCurrent(any())).thenReturn(current);
        when(mapper.selectCurrentForUpdate(any())).thenReturn(current);
        when(mapper.normalizeEmptyYear(any())).thenReturn(1);
        assertEquals("UPDATED",service.preview(request()).getFirst().action());
        verify(mapper,never()).normalizeEmptyYear(any());
        assertEquals("UPDATED",service.apply(request()).getFirst().action());
        verify(mapper,times(1)).normalizeEmptyYear(any());
    }
    @Test void replayAcceptsOnlyCompleteNormalizedRecord() {
        when(mapper.selectCurrentForUpdate(any())).thenReturn(new Current(2L,null,0,after,after));
        assertEquals("UNCHANGED",service.apply(request()).getFirst().action());
        verify(mapper,never()).normalizeEmptyYear(any());
    }
    @Test void changedAuditBusinessDeletedAndNonemptyYearBlockBeforeAnyWrite() {
        for(var current:List.of(new Current(2L,"  ",0,"c".repeat(64),after),
                new Current(2L,"  ",1,before,after),new Current(2L,"0 ",0,before,after),
                new Current(2L,null,0,"c".repeat(64),"c".repeat(64)))) {
            when(mapper.selectCurrentForUpdate(any())).thenReturn(current);
            assertThrows(IllegalArgumentException.class,()->service.apply(request()));
        }
        verify(mapper,never()).normalizeEmptyYear(any());
    }
    @Test void tenantOwnerAndDuplicateTargetGuardsRejectRequests() {
        assertThrows(IllegalArgumentException.class,()->service.preview(new Request(2L,"integration:2:100",request().entries())));
        assertThrows(IllegalArgumentException.class,()->service.preview(new Request(1L,"manual",request().entries())));
        when(mapper.selectCurrentForUpdate(any())).thenReturn(new Current(2L," ",0,before,after));
        var entry=request().entries().getFirst();
        assertThrows(IllegalArgumentException.class,()->service.apply(new Request(1L,"integration:1:100",List.of(entry,entry))));
        verify(mapper,never()).normalizeEmptyYear(any());
    }
}
