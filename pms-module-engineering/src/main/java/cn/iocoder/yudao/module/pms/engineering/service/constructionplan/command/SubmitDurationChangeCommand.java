package cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command;

public record SubmitDurationChangeCommand(
        Long planId, Long changeId, Integer expectedChangeVersion,
        Integer expectedProjectVersion, String idempotencyKey, String requestDigest) {
}
