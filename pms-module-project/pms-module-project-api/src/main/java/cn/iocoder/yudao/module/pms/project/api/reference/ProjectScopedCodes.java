package cn.iocoder.yudao.module.pms.project.api.reference;

import java.util.Collection;
import java.util.Locale;

/**
 * 项目域操作记录内部编码生成规则（纯函数，供各业务模块复用）。
 * <p>
 * 编码格式 "{项目编码}-{类型码}-{三位序号}"，如 PROJ-2026-001-AZ-001；
 * 序号取同项目同类型既有编码的最大序号 +1，并跳过与既有编码的冲突。
 * 项目编码本身允许包含 "-"，序号一律取编码末段解析。
 */
public final class ProjectScopedCodes {

    private ProjectScopedCodes() {
    }

    /**
     * 生成项目内唯一编码。
     *
     * @param projectCode   项目编码
     * @param typeCode      记录类型码（如 AZ=安装、PZ=配置）
     * @param existingCodes 同项目内既有编码全集（含历史手工编码）
     * @return 未被占用的下一个编码
     */
    public static String next(String projectCode, String typeCode, Collection<String> existingCodes) {
        String prefix = projectCode + "-" + typeCode.toUpperCase(Locale.ROOT) + "-";
        int maxSeq = 0;
        if (existingCodes != null) {
            for (String code : existingCodes) {
                int seq = parseSeq(prefix, code);
                if (seq > maxSeq) {
                    maxSeq = seq;
                }
            }
        }
        String candidate = format(prefix, maxSeq + 1);
        while (existingCodes != null && existingCodes.contains(candidate)) {
            maxSeq++;
            candidate = format(prefix, maxSeq + 1);
        }
        return candidate;
    }

    private static int parseSeq(String prefix, String code) {
        if (code == null || !code.startsWith(prefix)) {
            return 0;
        }
        String tail = code.substring(prefix.length());
        if (tail.indexOf('-') >= 0) {
            return 0; // 序号段不允许再含 "-"，非标准编码不参与推号
        }
        try {
            int value = Integer.parseInt(tail);
            return value > 0 ? value : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String format(String prefix, int seq) {
        return prefix + String.format(Locale.ROOT, "%03d", seq);
    }
}
