package cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto;

public record DynamicFormInstanceRevalidationQuery(Long actorUserId, DynamicFormInstanceFact expectedFact) {
}
