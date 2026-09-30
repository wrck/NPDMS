package cn.iocoder.yudao.module.pms.platform.api.collection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Shared collection application, also used by legacy business REST adapters. */
public interface CollectionApplicationApi {
    CollectionSourceAdapter.Source context(String entry, Long objectId, Long actorId);
    Execution submit(String entry, Long objectId, Long actorId, CollectionExecutionRequest request);
    PageResult<Execution> page(String entry, Long objectId, Long actorId, int pageNo, int pageSize);
    Execution findByRequestKey(String entry, Long objectId, Long actorId, String requestKey);
    Execution consume(String entry, Long objectId, Long actorId, Long executionId);
    void cancel(String entry, Long objectId, Long actorId, Long executionId);
    String download(String entry, Long objectId, Long actorId, Long executionId);
    List<Map<String, Object>> semanticResults(String entry, Long objectId, Long actorId, Long executionId);
    record Execution(Long id, Long actorId, LocalDateTime createdAt, Long consumedResultVersion,
                     CollectionTaskDTO task, String commandText, String templateName, Long retryOfId) { }
}
