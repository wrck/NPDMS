package cn.iocoder.yudao.module.pms.engineering.service.preparation.command;

public record PreparationReviewCommand(String action, Long preparationId, Long itemId,
        Integer expectedPreparationVersion, Integer expectedItemVersion,
        Integer expectedProjectVersion, String reason, String idempotencyKey) {
    public static final String SUBMIT = "SUBMIT";
    public static final String CONFIRM = "CONFIRM";
    public static final String CONFIRM_NOT_APPLICABLE = "CONFIRM_NOT_APPLICABLE";
    public static final String RETURN = "RETURN";
}
