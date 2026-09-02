package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleSecurityReviewDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleChildrenQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InspectionRuleSecurityReviewMapper extends BaseMapperX<InspectionRuleSecurityReviewDO> {

    default List<InspectionRuleSecurityReviewDO> selectListValidByRevisionIds(InspectionRuleChildrenQuery query) {
        if (query.revisionIds() == null || query.revisionIds().isEmpty()) {
            return List.of();
        }
        return selectListValidByRevisionIdsInternal(query);
    }

    List<InspectionRuleSecurityReviewDO> selectListValidByRevisionIdsInternal(
            @Param("query") InspectionRuleChildrenQuery query);
}
