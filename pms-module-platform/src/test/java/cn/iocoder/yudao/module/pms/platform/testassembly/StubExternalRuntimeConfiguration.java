package cn.iocoder.yudao.module.pms.platform.testassembly;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsResourceApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchResult;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsTaskSnapshot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 测试装配桩：本应用不装配资产/集成等外部执行运行时，相关公共能力请求
 * 显式报告能力不可用，不伪造执行结果；本次统一模型闭环不会触达这些接口。
 */
@Configuration(proxyBeanMethods = false)
public class StubExternalRuntimeConfiguration {

    private BusinessContractException absent(String capability) {
        return new BusinessContractException("EXTERNAL_RUNTIME_ABSENT",
                "本应用未装配 " + capability + " 运行时，能力不可用");
    }

    @Bean
    public ProjectDeviceSelectionApi projectDeviceSelectionApi() {
        return new ProjectDeviceSelectionApi() {
            @Override
            public List<SelectedProjectDevice> validateSelection(Long projectId, List<Long> deviceIds) {
                throw absent("资产设备");
            }
        };
    }

    @Bean
    public DeviceOpsGatewayApi deviceOpsGatewayApi() {
        return new DeviceOpsGatewayApi() {
            @Override
            public DeviceOpsDispatchResult dispatch(DeviceOpsDispatchCommand command) {
                throw absent("设备运维网关");
            }

            @Override
            public DeviceOpsTaskSnapshot query(String platformTaskId) {
                throw absent("设备运维网关");
            }

            @Override
            public void cancel(String platformTaskId, String reason) {
                throw absent("设备运维网关");
            }
        };
    }

    @Bean
    public DeviceOpsResourceApi deviceOpsResourceApi() {
        return new DeviceOpsResourceApi() {
            @Override
            public Connection saveConnection(ConnectionCommand command) {
                throw absent("设备运维资源");
            }

            @Override
            public Connection getConnection(String id) {
                throw absent("设备运维资源");
            }

            @Override
            public void registerScript(String key, String version, String content, String sha256) {
                throw absent("设备运维资源");
            }
        };
    }
}
