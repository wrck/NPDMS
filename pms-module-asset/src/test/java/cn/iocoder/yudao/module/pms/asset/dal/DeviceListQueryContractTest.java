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
        VisibleDevicePageQuery query = VisibleDevicePageQuery.builder()
                .tenantId(1L)
                .visibleProjectIds(Set.of())
                .pageNo(1)
                .pageSize(20)
                .visibleContractNumbers(Set.of())
                .organizationGrants(java.util.List.of()).build();
        PageResult<?> result = DeviceMapper.emptyWhenInvisible(query);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void visibilityScopeRepresentsProjectsRatherThanCallerSuppliedDeviceIds() {
        assertEquals("visibleProjectIds", VisibleDevicePageQuery.class.getRecordComponents()[1].getName());
    }

    @Test
    void mapperUsesSingleScenarioQueryObject() throws Exception {
        Method method = DeviceMapper.class.getMethod("selectVisibleDevicePage", VisibleDevicePageQuery.class);
        assertEquals(1, method.getParameterCount());
    }
}
