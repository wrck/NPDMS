package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleCommandRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleChildrenQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InspectionRuleCommandRevisionMapper extends BaseMapperX<InspectionRuleCommandRevisionDO> {

    default List<InspectionRuleCommandRevisionDO> selectListByRevisionIds(InspectionRuleChildrenQuery query) {
        if (query.revisionIds() == null || query.revisionIds().isEmpty()) {
            return List.of();
        }
        return selectListByRevisionIdsInternal(query);
    }

    List<InspectionRuleCommandRevisionDO> selectListByRevisionIdsInternal(
            @Param("query") InspectionRuleChildrenQuery query);
}
