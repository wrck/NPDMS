package cn.iocoder.yudao.module.pms.asset.dal;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.VisibleDevicePageQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceListQueryContractTest {

    @Test
    void shouldReturnEmptyPageForEmptyVisibilityScope() {
        VisibleDevicePageQuery query = new VisibleDevicePageQuery(
                1L, Set.of(), null, null, null, null, 1, 20);
        PageResult<?> result = DeviceMapper.emptyWhenInvisible(query);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void mapperUsesSingleScenarioQueryObject() throws Exception {
        Method method = DeviceMapper.class.getMethod("selectVisibleDevicePage", VisibleDevicePageQuery.class);
        assertEquals(1, method.getParameterCount());
    }
}
