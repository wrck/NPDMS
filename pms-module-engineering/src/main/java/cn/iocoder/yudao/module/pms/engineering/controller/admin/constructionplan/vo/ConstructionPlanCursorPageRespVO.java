package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstructionPlanCursorPageRespVO<T> {
    private List<T> items;
    private String nextCursor;
    private Boolean hasMore;
}
