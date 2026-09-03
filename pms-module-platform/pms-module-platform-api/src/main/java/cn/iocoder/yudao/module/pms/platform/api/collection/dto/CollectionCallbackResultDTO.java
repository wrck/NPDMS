package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

public record CollectionCallbackResultDTO(
        String callbackId,
        String platformTaskId,
        String status,
        String technicalStage,
        Long resultVersion,
        Long fileVersionId,
        String quarantineEvidenceId,
        boolean duplicate) {
}
