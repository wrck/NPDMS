package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops.DeviceOpsCallbackReceiptDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.deviceops.DeviceOpsCallbackReceiptMapper;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionLogIngestionApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class DacCallbackService {
    private final DeviceOpsCallbackReceiptMapper receipts;
    private final CollectionLogIngestionApi ingestion;

    @Transactional(rollbackFor = Exception.class)
    public Ack receive(DacCallbackMetadata metadata, String evidenceDigest, InputStream log) {
        var existing = receipts.find(metadata.tenantId(), metadata.callbackId());
        if (existing != null) {
            if (existing.getResponseJson() == null || !evidenceDigest.equals(existing.getEvidenceDigest())) {
                throw new IllegalStateException("DAC_CALLBACK_IDEMPOTENCY_CONFLICT");
            }
            return new Ack(metadata.callbackId(), existing.getId(), "ACKNOWLEDGED");
        }
        var receipt = new DeviceOpsCallbackReceiptDO();
        receipt.setTenantId(metadata.tenantId());
        receipt.setCallbackId(metadata.callbackId());
        receipt.setPlatformTaskId(metadata.platformTaskId());
        receipt.setExternalTaskId(metadata.externalTaskId());
        receipt.setEvidenceDigest(evidenceDigest);
        // The unique receipt is inserted before ingestion. Concurrent delivery retries after the winner commits.
        if (receipts.insert(receipt) != 1 || receipt.getId() == null) {
            throw new IllegalStateException("DAC_CALLBACK_RECEIPT_CREATE_FAILED");
        }
        CollectionCallbackResultDTO result = ingestion.ingest(new CollectionLogIngestionApi.Command(
                metadata.tenantId(), receipt.getId(), metadata.callbackId(), metadata.platformTaskId(),
                metadata.externalTaskId(), metadata.externalStatus(), metadata.sequence(), metadata.resultVersion(),
                metadata.sizeBytes(), metadata.sha256(), metadata.failureCategory(), metadata.traceId()), log);
        if ("RECONCILING".equals(result.technicalStage())) throw new IllegalStateException("DAC_CALLBACK_SEQUENCE_GAP");
        receipt.setResponseJson(JsonUtils.toJsonString(result));
        if (receipts.updateById(receipt) != 1) {
            throw new IllegalStateException("DAC_CALLBACK_RECEIPT_ACK_FAILED");
        }
        return new Ack(metadata.callbackId(), receipt.getId(), "ACKNOWLEDGED");
    }

    public record Ack(String callbackId, Long receiptId, String status) { }
}
