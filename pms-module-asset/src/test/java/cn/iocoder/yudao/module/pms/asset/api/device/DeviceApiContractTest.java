package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceSummaryDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceApiContractTest {

    @Test
    void shouldExposeStableDeviceSummaryWithoutPersistenceTypes() {
        assertTrue(DeviceSummaryDTO.class.isRecord());
        Set<String> fields = Arrays.stream(DeviceSummaryDTO.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "deviceId", "tenantId", "sn", "productCode", "productModel", "productName",
                "shipmentTime", "packageNo", "contractNo", "shipmentRecordId",
                "projectId", "projectAssignmentVersion", "customerId", "customerAssignmentVersion",
                "warrantyStartDate", "warrantyEndDate", "warrantyStatus",
                "conpVersion", "conpType", "conpSeries", "conpMark"), fields);
        assertTrue(Arrays.stream(DeviceQueryApi.class.getMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(type -> type.getSimpleName().endsWith("DO")));
    }
}
