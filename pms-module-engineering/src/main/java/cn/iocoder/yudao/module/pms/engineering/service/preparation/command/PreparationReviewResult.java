package cn.iocoder.yudao.module.pms.engineering.service.preparation.command;

public record PreparationReviewResult(Long preparationId, Integer businessVersion,
                                      String statusCode, Integer preparationVersion,
                                      Long currentPreparationId) {}
