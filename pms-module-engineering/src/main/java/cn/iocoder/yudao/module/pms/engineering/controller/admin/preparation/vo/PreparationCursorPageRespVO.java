package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import java.util.List;

public record PreparationCursorPageRespVO<T>(List<T> items, String nextCursor, boolean hasMore) {
    public PreparationCursorPageRespVO {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
