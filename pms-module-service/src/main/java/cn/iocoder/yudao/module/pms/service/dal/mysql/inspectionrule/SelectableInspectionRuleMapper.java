package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule;

import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection.SelectableInspectionRuleProjection;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.SelectableInspectionRuleQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SelectableInspectionRuleMapper {

    default List<SelectableInspectionRuleProjection> selectListSelectable(SelectableInspectionRuleQuery query) {
        if (query.productTypeCodes() == null || query.productTypeCodes().isEmpty()) {
            return List.of();
        }
        return selectListSelectableInternal(query);
    }

    List<SelectableInspectionRuleProjection> selectListSelectableInternal(
            @Param("query") SelectableInspectionRuleQuery query);
}
