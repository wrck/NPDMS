package cn.iocoder.yudao.module.pms.platform.api.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialDTO;

public interface DeviceCredentialApi {

    DeviceCredentialDTO create(DeviceCredentialCreateCommand command);

    DeviceCredentialDTO get(Long tenantId, Long credentialId);
}
