package cn.iocoder.yudao.module.pms.project.api.satisfaction.dto;

public record SatisfactionTaskInitializationResult(String outcome, Long taskId, Long questionnaireId,
        String collectionKey, Integer taskRevisionNo, Integer taskVersion) {
}
