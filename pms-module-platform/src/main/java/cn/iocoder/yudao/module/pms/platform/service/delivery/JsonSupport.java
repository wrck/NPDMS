package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.util.List;

/** 交付模块内部 JSON 解析（Jackson 3 TypeReference 统一收口）。 */
public final class JsonSupport {

    public static List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JsonUtils.parseObject(json, new tools.jackson.core.type.TypeReference<List<Long>>() {});
    }

    private JsonSupport() {
    }
}
