package cn.iocoder.yudao.module.pms.workflow.service;

import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalFieldPermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 敏感字段脱敏器（Story 6）。
 *
 * <p>审批人打开审批详情时，后端根据 {@link ApprovalFieldPermission} 配置对业务字段脱敏后返回。</p>
 *
 * <p>脱敏规则：</p>
 * <ul>
 *   <li>{@code VISIBLE} → 返回原值</li>
 *   <li>{@code HIDDEN} → 返回 {@code null}</li>
 *   <li>{@code MASKED} → 按 {@code maskPattern} 处理：
 *     <ul>
 *       <li>{@code phone-mask}：{@code 13812345678} → {@code 138****5678}（保留前 3 后 4）</li>
 *       <li>{@code amount-mask}：{@code 12345.67} → {@code 12***.67}（保留前 2 位整数，整数余部脱敏，保留小数）</li>
 *       <li>{@code email-mask}：{@code alice@example.com} → {@code a***@example.com}（本地部分保留首字符）</li>
 *       <li>{@code custom}：使用 {@code customPattern} 正则，匹配部分替换为 {@code ***}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>关联设计文档：§3.5 Story 6 验收 1 敏感字段脱敏（行 444-470）、§5.7（行 1094-1121）。</p>
 */
@Slf4j
@Component
public class SensitiveFieldMasker {

    /**
     * 对单个字段值按权限脱敏。
     *
     * @param entityType   业务实体类名（用于日志与未来扩展）
     * @param fieldName    字段名（用于日志）
     * @param value        原始值
     * @param permission   字段权限配置（为 null 视为 VISIBLE）
     * @return 脱敏后的值（HIDDEN 返回 null，VISIBLE 返回原值，MASKED 返回脱敏值）
     */
    public Object mask(String entityType, String fieldName, Object value, ApprovalFieldPermission permission) {
        if (value == null) {
            return null;
        }
        if (permission == null) {
            return value;
        }
        String perm = permission.getPermission();
        if (perm == null || "VISIBLE".equalsIgnoreCase(perm)) {
            return value;
        }
        if ("HIDDEN".equalsIgnoreCase(perm)) {
            return null;
        }
        if ("MASKED".equalsIgnoreCase(perm)) {
            return maskValue(value, permission.getMaskPattern(), permission.getCustomPattern());
        }
        return value;
    }

    /**
     * 对业务数据 Map 按权限列表批量脱敏。
     *
     * <p>仅对 {@code permissions} 中出现的字段进行处理，未配置的字段保持原值（VISIBLE 语义）。
     * HIDDEN 字段会从 Map 中移除（不返回）。返回的是新 Map，不修改入参。</p>
     *
     * @param businessData 业务数据 Map（fieldName → value）
     * @param permissions  字段权限列表
     * @return 脱敏后的新 Map
     */
    public Map<String, Object> maskMap(Map<String, Object> businessData, List<ApprovalFieldPermission> permissions) {
        Map<String, Object> result = new LinkedHashMap<>(businessData);
        if (permissions == null || permissions.isEmpty()) {
            return result;
        }
        for (ApprovalFieldPermission perm : permissions) {
            String field = perm.getFieldName();
            if (field == null || !result.containsKey(field)) {
                continue;
            }
            Object val = result.get(field);
            if ("HIDDEN".equalsIgnoreCase(perm.getPermission())) {
                result.remove(field);
            } else {
                result.put(field, mask(perm.getEntityType(), field, val, perm));
            }
        }
        return result;
    }

    // ===================== 内部脱敏实现 =====================

    private Object maskValue(Object value, String maskPattern, String customPattern) {
        if (value == null) {
            return null;
        }
        if (maskPattern == null) {
            return value;
        }
        String str = String.valueOf(value);
        return switch (maskPattern) {
            case "phone-mask" -> maskPhone(str);
            case "amount-mask" -> maskAmount(str);
            case "email-mask" -> maskEmail(str);
            case "custom" -> maskCustom(str, customPattern);
            default -> {
                log.warn("未知脱敏规则 maskPattern={}，返回原值", maskPattern);
                yield value;
            }
        };
    }

    /** 手机号脱敏：保留前 3 后 4，中间用 **** 占位。 */
    private String maskPhone(String phone) {
        if (phone.length() <= 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 金额脱敏：保留前 2 位整数，整数余部用 *** 替换，保留小数部分。例如 12345.67 → 12***.67。 */
    private String maskAmount(String amount) {
        int dotIdx = amount.indexOf('.');
        String intPart = dotIdx >= 0 ? amount.substring(0, dotIdx) : amount;
        String decPart = dotIdx >= 0 ? amount.substring(dotIdx) : "";
        if (intPart.length() <= 2) {
            return "***" + decPart;
        }
        return intPart.substring(0, 2) + "***" + decPart;
    }

    /** 邮箱脱敏：本地部分保留首字符，余部用 ***，保留域名。例如 alice@example.com → a***@example.com。 */
    private String maskEmail(String email) {
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) {
            return "****";
        }
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (local.length() <= 1) {
            return "*" + "***" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }

    /** 自定义正则脱敏：匹配 customPattern 的部分替换为 ***。 */
    private String maskCustom(String value, String customPattern) {
        if (customPattern == null || customPattern.isBlank()) {
            return value;
        }
        try {
            return Pattern.compile(customPattern).matcher(value).replaceAll("***");
        } catch (Exception e) {
            log.warn("自定义脱敏正则无效 customPattern={}，返回原值", customPattern);
            return value;
        }
    }
}
