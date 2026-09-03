package cn.iocoder.yudao.module.pms.asset.service.producttype;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class AssetProductTypeSourceOrder {

    public enum Decision {
        NEWER,
        IDEMPOTENT_REPLAY,
        STALE_SOURCE,
        SOURCE_CONFLICT
    }

    public Decision decide(LocalDateTime incomingUpdatedAt, String incomingVersion, String incomingPayloadHash,
                           String incomingProductTypeCode, LocalDateTime currentUpdatedAt, String currentVersion,
                           String currentPayloadHash, String currentProductTypeCode) {
        if (incomingUpdatedAt == null) {
            throw new IllegalArgumentException("来源更新时间不能为空");
        }
        if (currentUpdatedAt == null || incomingUpdatedAt.isAfter(currentUpdatedAt)) {
            return Decision.NEWER;
        }
        if (incomingUpdatedAt.isBefore(currentUpdatedAt)) {
            return Decision.STALE_SOURCE;
        }
        return Objects.equals(incomingVersion, currentVersion)
                && Objects.equals(incomingPayloadHash, currentPayloadHash)
                && Objects.equals(incomingProductTypeCode, currentProductTypeCode)
                ? Decision.IDEMPOTENT_REPLAY : Decision.SOURCE_CONFLICT;
    }
}
