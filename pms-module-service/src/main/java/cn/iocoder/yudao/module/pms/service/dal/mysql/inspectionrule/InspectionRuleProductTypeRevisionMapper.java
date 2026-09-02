package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleProductTypeRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleProductTypeNameUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleChildrenQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InspectionRuleProductTypeRevisionMapper extends BaseMapperX<InspectionRuleProductTypeRevisionDO> {

    default List<InspectionRuleProductTypeRevisionDO> selectListByRevisionIds(InspectionRuleChildrenQuery query) {
        if (query.revisionIds() == null || query.revisionIds().isEmpty()) {
            return List.of();
        }
        return selectListByRevisionIdsInternal(query);
    }

    List<InspectionRuleProductTypeRevisionDO> selectListByRevisionIdsInternal(
            @Param("query") InspectionRuleChildrenQuery query);

    int updateNameSnapshot(@Param("command") InspectionRuleProductTypeNameUpdate command);

    default int hardDeleteByRevisionIds(InspectionRuleChildrenQuery query) {
        if (query.revisionIds() == null || query.revisionIds().isEmpty()) {
            return 0;
        }
        return hardDeleteByRevisionIdsInternal(query);
    }

    int hardDeleteByRevisionIdsInternal(@Param("query") InspectionRuleChildrenQuery query);
}
