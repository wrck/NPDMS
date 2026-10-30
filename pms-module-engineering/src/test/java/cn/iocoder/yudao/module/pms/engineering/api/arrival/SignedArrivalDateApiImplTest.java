package cn.iocoder.yudao.module.pms.engineering.api.arrival;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.LatestSignedArrivalQuery;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignedArrivalDateApiImplTest {
    final ArrivalMapper mapper=mock(ArrivalMapper.class);
    final SignedArrivalDateApiImpl api=new SignedArrivalDateApiImpl(mapper);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(7L);}
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void returnsLatestBusinessDateOrNullWithoutInventingADate(){
        var query=new LatestSignedArrivalQuery(7L,9L);
        when(mapper.selectLatestSignedArrivalTime(query)).thenReturn(LocalDateTime.of(2026,10,20,23,30));
        assertEquals(java.time.LocalDate.of(2026,10,20),api.latest(new SignedArrivalDateApi.Query(7L,9L)));
        when(mapper.selectLatestSignedArrivalTime(query)).thenReturn(null);
        assertNull(api.latest(new SignedArrivalDateApi.Query(7L,9L)));
    }
    @Test void rejectsInvalidProjectAndForeignTenantBeforeQuery(){
        assertThrows(IllegalArgumentException.class,()->api.latest(new SignedArrivalDateApi.Query(8L,9L)));
        assertThrows(IllegalArgumentException.class,()->api.latest(new SignedArrivalDateApi.Query(7L,null)));
        assertThrows(IllegalArgumentException.class,()->api.latest(new SignedArrivalDateApi.Query(7L,0L)));
        verifyNoInteractions(mapper);
    }
}
