package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;

import java.util.List;

public interface AssetDeviceScopeApi {

    SerialScopeValidationResult validateAssignableSerials(Long tenantId, Long parentProjectId,
                                                           List<String> serialNumbers);
}
