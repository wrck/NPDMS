package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;

public record CommerceAuthorityBatchResult(String eventId, String batchId, Decision decision) {

    public CommerceAuthorityBatchResult {
        try {
            eventId = CommerceAuthorityContractRules.text(eventId, 128, "eventId");
            batchId = CommerceAuthorityContractRules.text(batchId, 128, "batchId");
        } catch (CommerceAuthorityIngestException ex) {
            throw corrupted(ex.getMessage());
        }
        if (decision == null) {
            throw corrupted("decision must not be null");
        }
    }

    public enum Decision {
        ACCEPTED,
        ACCEPTED_NO_CHANGE,
        EVENT_REPLAYED
    }

    private static CommerceAuthorityIngestException corrupted(String message) {
        return new CommerceAuthorityIngestException(
                CommerceAuthorityIngestException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
