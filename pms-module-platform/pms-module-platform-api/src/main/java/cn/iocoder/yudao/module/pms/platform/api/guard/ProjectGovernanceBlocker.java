package cn.iocoder.yudao.module.pms.platform.api.guard;

/** 跨域守卫最小阻断引用，不承载业务正文。 */
public record ProjectGovernanceBlocker(
        String objectType,
        String objectId,
        String status,
        String code,
        String summary) {

    public ProjectGovernanceBlocker {
        objectType = requireText(objectType, "objectType");
        objectId = requireText(objectId, "objectId");
        status = requireText(status, "status");
        code = requireText(code, "code");
        summary = requireText(summary, "summary");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
