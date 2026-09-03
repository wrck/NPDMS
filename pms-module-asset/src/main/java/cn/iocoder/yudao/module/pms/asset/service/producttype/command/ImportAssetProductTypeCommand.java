package cn.iocoder.yudao.module.pms.asset.service.producttype.command;

import java.time.LocalDateTime;
import java.util.List;

public record ImportAssetProductTypeCommand(
        String operationId,
        String idempotencyKey,
        String productTypeCode,
        String displayName,
        boolean enabled,
        String sourceSystem,
        String sourceKey,
        String sourceVersion,
        LocalDateTime sourceUpdatedAt,
        String payloadHash,
        List<DeviceCurrentProductTypeInput> devices) {

    public ImportAssetProductTypeCommand {
        devices = devices == null ? List.of() : List.copyOf(devices);
    }
}
