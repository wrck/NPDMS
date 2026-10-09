package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import java.util.List;

/** Versioned, engine-neutral conditions. References name public fields, owned collections or delivery types. */
public record BusinessCondition(Kind kind,String fieldCode,BusinessFieldFilter.Operator operator,
                                List<Object> values,List<BusinessCondition> children) {
    public enum Kind { FIELD, DELIVERY, ALL, ANY, NOT, MEMBER_ALL, MEMBER_ANY, COUNT }
    public BusinessCondition { values=values==null?List.of():List.copyOf(values);children=children==null?List.of():List.copyOf(children); }
    public static BusinessCondition field(String code,BusinessFieldFilter.Operator operator,Object value){
        return new BusinessCondition(Kind.FIELD,code,operator,value==null?List.of():List.of(value),List.of());
    }
}
