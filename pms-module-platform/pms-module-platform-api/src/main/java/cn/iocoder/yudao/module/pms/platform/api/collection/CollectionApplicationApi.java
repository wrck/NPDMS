package cn.iocoder.yudao.module.pms.platform.api.collection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Shared collection application, also used by legacy business REST adapters. */
public interface CollectionApplicationApi {
    CollectionSourceAdapter.Source context(SourceQuery query);
    Execution submit(SourceQuery query, CollectionExecutionRequest request);
    PageResult<Execution> page(SourceQuery query, PageParam pagination);
    Execution findByRequestKey(SourceQuery query, String requestKey);
    Execution consume(SourceQuery query, Long executionId);
    void cancel(SourceQuery query, Long executionId);
    String download(SourceQuery query, Long executionId);
    List<Map<String, Object>> semanticResults(SourceQuery query, Long executionId);

    @Builder
    record SourceQuery(String entry, Long objectId, Long actorId) { }
    record Execution(Long id, Long actorId, LocalDateTime createdAt, Long consumedResultVersion,
                     CollectionTaskDTO task, String commandText, String templateName, Long retryOfId, CollectionExecutionProfile executionProfile) {
        public Execution(Long id, Long actorId, LocalDateTime createdAt, Long consumedResultVersion,
                         CollectionTaskDTO task, String commandText, String templateName, Long retryOfId) {
            this(id, actorId, createdAt, consumedResultVersion, task, commandText, templateName, retryOfId, null);
        }
    }
}
