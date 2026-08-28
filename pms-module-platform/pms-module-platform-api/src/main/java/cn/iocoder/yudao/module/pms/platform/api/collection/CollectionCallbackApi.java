package cn.iocoder.yudao.module.pms.platform.api.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackResultDTO;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionConsumptionCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionConsumptionResultDTO;

public interface CollectionCallbackApi {

    CollectionCallbackResultDTO handleCallback(CollectionCallbackCommand command);

    CollectionConsumptionResultDTO confirmConsumption(CollectionConsumptionCommand command);
}
