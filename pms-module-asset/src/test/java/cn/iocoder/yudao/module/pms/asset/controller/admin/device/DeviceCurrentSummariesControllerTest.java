package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.device.vo.DeviceSummaryBatchReqVO;
import cn.iocoder.yudao.module.pms.asset.service.device.DeviceDetailService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceCurrentSummariesControllerTest {
    @Mock DeviceDetailService details;
    @InjectMocks DeviceController controller;

    @Test void batchUsesCurrentIdentityAndKeepsLargeWireIdsAndOptionalFields() throws Exception {
        try (var mocks = MockitoAnnotations.openMocks(this)) {
            TenantContextHolder.setTenantId(1L);
            SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
            try {
                var mapper = tools.jackson.databind.json.JsonMapper.builder().addModule(
                        new cn.iocoder.yudao.framework.jackson.config.YudaoJacksonAutoConfiguration().timestampSupportModuleBean()).build();
                var request = mapper.readValue("{\"deviceIds\":[\"2113043793829797942\",8],\"projectId\":\"2113043793829797943\"}", DeviceSummaryBatchReqVO.class);
                assertEquals(Set.of(2113043793829797942L, 8L), request.getDeviceIds());
                var summary = new DeviceSummaryDTO(2113043793829797942L, 1L, "SN", "CODE", "MODEL", "产品",
                        null, null, null, null, 10L, 1L, null, null, null, null, null, null, null, null, null,
                        "设备", null, "当前描述");
                when(details.getCurrentSummaries(any())).thenReturn(List.of(summary));

                var result = controller.getCurrentSummaries(request);

                verify(details).getCurrentSummaries(argThat(q -> q.tenantId().equals(1L) && q.actorId().equals(7L)
                        && q.deviceIds().equals(request.getDeviceIds()) && q.projectId().equals(2113043793829797943L)));
                assertEquals(List.of(summary), result.getData());
                var json = mapper.writeValueAsString(result);
                assertTrue(json.contains("\"deviceId\":\"2113043793829797942\""));
                assertTrue(json.contains("\"name\":\"设备\""));
                assertTrue(json.contains("\"productDesc\":\"当前描述\""));
                assertNull(result.getData().getFirst().locationSnapshot());
            } finally {
                TenantContextHolder.clear(); SecurityContextHolder.clearContext();
            }
        }
    }
}
