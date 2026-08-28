package cn.iocoder.yudao.module.pms.platform.api.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionBatchCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionBatchDTO;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;

public interface CollectionTaskApi {

    CollectionBatchDTO createBatch(CollectionBatchCreateCommand command);

    CollectionTaskDTO getTask(Long tenantId, String platformTaskId);
}
