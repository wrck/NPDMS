package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.hutool.core.lang.Validator;

/** CUS-04 revision 020: project-local changes never imply customer-master changes. */
public final class ContactRules {
    private ContactRules() {}

    public static ContactValues normalize(ContactValues values, int status) {
        if (values == null || status < 0 || status > 1) throw new IllegalArgumentException("联系人状态无效");
        var normalized = new ContactValues(text(values.name(), 64), text(values.department(), 64),
                text(values.title(), 64), text(values.mobile(), 32), text(values.phone(), 32),
                text(values.email(), 128), text(values.roleCode(), 64), text(values.remark(), 500));
        if (normalized.name() == null) throw new IllegalArgumentException("联系人姓名不能为空");
        validatePhone(normalized.mobile());
        validatePhone(normalized.phone());
        if (normalized.email() != null && !Validator.isEmail(normalized.email())) {
            throw new IllegalArgumentException("邮箱格式无效");
        }
        if (status == 0 && normalized.mobile() == null && normalized.phone() == null && normalized.email() == null) {
            throw new IllegalArgumentException("启用联系人至少填写一种有效联系方式");
        }
        return normalized;
    }

    public static void requirePrimaryAllowed(int status, boolean primary, Long currentPrimaryId, Long targetId) {
        if (!primary) return;
        if (status != 0) throw new IllegalArgumentException("停用联系人不能设为主联系人");
        if (currentPrimaryId != null && !currentPrimaryId.equals(targetId)) {
            throw new IllegalArgumentException("项目已存在主联系人，请先取消原主联系人主标识");
        }
    }

    public static void requireRemovalAllowed(boolean primary, boolean confirmNoPrimary) {
        if (primary && !confirmNoPrimary) {
            throw new IllegalArgumentException("请先指定新主联系人，或明确确认项目暂不设置主联系人");
        }
    }

    private static void validatePhone(String value) {
        if (value != null && (!value.matches("[+()\\-\\s0-9]+") || value.chars().noneMatch(Character::isDigit))) {
            throw new IllegalArgumentException("联系电话格式无效");
        }
    }

    private static String text(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException("联系人字段长度超出限制");
        return normalized;
    }
}
