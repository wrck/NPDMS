package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionFeedbackExportDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InspectionFeedbackExportMapper extends BaseMapperX<InspectionFeedbackExportDO> {
    InspectionFeedbackExportDO selectForUpdate(@Param("query") InspectionFeedbackExportQuery query);
    default PageResult<InspectionFeedbackExportDO> selectMine(InspectionFeedbackExportPageQuery query) {
        return selectPage(query.page(), new LambdaQueryWrapperX<InspectionFeedbackExportDO>()
                .eq(InspectionFeedbackExportDO::getTenantId, query.tenantId()).eq(InspectionFeedbackExportDO::getExportedBy, query.actorId())
                .orderByDesc(InspectionFeedbackExportDO::getId));
    }
}
