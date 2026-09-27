package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import java.util.List;

/** 类型化字段筛选。空权限集合或空集合筛选必须返回空结果，不得因省略条件扩大查询范围。 */
public record BusinessFieldFilter(String fieldCode, Operator operator, List<Object> values) {

    public enum Operator { EQ, NE, IN, LIKE, GT, GTE, LT, LTE, IS_NULL, NOT_NULL }
}
