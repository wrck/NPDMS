package cn.iocoder.yudao.module.pms.platform.api.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackResultDTO;
import java.io.InputStream;

/** Authenticated INT ingress only. File bytes are streamed directly to the file owner. */
public interface CollectionLogIngestionApi {
    CollectionCallbackResultDTO ingest(Command command, InputStream log);

    record Command(Long tenantId, Long receiptId, String callbackId, String platformTaskId,
                   String externalTaskId, String externalStatus, long sequence, long resultVersion,
                   long sizeBytes, String sha256, String failureCategory, String traceId) { }
}
