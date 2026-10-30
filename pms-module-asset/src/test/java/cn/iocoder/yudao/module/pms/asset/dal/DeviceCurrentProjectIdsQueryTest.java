package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.CurrentProjectDeviceIdsQuery;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceCurrentProjectIdsQueryTest {
    @Test void emptyOrMissingScopeNeverExecutesAQuery() {
        var mapper = mock(DeviceMapper.class, CALLS_REAL_METHODS);
        for (var query : List.of(new CurrentProjectDeviceIdsQuery(1L, 10L, Set.of()),
                new CurrentProjectDeviceIdsQuery(1L, 10L, null), new CurrentProjectDeviceIdsQuery(1L, null, Set.of(8L)),
                new CurrentProjectDeviceIdsQuery(null, 10L, Set.of(8L)))) {
            assertTrue(mapper.selectCurrentProjectDevicesByIds(query).isEmpty());
        }
        verify(mapper, never()).selectList(any(Wrapper.class));
    }

    @Test void fullProjectReadUsesOnlyTenantProjectAndRequestedIdsWithoutContractOrSourceConditions() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), DeviceDO.class);
        var mapper = mock(DeviceMapper.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(mapper).selectList(any(Wrapper.class));
        mapper.selectCurrentProjectDevicesByIds(new CurrentProjectDeviceIdsQuery(1L, 10L, Set.of(8L, 9L)));
        var argument = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(argument.capture());
        var wrapper = (LambdaQueryWrapper<?>) argument.getValue();
        var sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("tenant_id =") && sql.contains("project_id =") && sql.contains("id IN"));
        assertTrue(sql.endsWith("ORDER BY id ASC"));
        assertFalse(sql.matches("(?s).*\\bOR\\b.*"));
        assertFalse(sql.toLowerCase().matches("(?s).*(contract|source|status|select|exists).*"));
        assertEquals(Set.of(1L, 10L, 8L, 9L), Set.copyOf(wrapper.getParamNameValuePairs().values()));
    }
}
