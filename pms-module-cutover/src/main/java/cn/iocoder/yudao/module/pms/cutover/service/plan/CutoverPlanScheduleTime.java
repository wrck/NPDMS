package cn.iocoder.yudao.module.pms.cutover.service.plan;

import tools.jackson.databind.JsonNode;

/** 方案页单个计划割接时间；缺省只允许草稿或历史读取，不伪造默认值。 */
public final class CutoverPlanScheduleTime {
    private CutoverPlanScheduleTime() {}

    public static Long value(JsonNode content) {
        if (content == null) return null;
        var node = content.get("scheduledTime");
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() && !node.isTextual()) throw new IllegalArgumentException("计划割接时间格式无效");
        try {
            long time = Long.parseLong(node.asText());
            if (time <= 0) throw new IllegalArgumentException("计划割接时间必须为正值");
            return time;
        } catch (NumberFormatException failure) { throw new IllegalArgumentException("计划割接时间格式无效"); }
    }
}
