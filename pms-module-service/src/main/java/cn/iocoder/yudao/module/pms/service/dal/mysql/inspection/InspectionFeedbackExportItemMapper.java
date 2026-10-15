package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionFeedbackExportItemDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionFeedbackRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionFeedbackExportItemMapper extends BaseMapperX<InspectionFeedbackExportItemDO> {
    default List<InspectionFeedbackExportItemDO> selectForExport(InspectionFeedbackExportQuery query) {
        return selectList(new LambdaQueryWrapperX<InspectionFeedbackExportItemDO>()
                .eq(InspectionFeedbackExportItemDO::getTenantId, query.tenantId()).eq(InspectionFeedbackExportItemDO::getExportId, query.exportId())
                .orderByAsc(InspectionFeedbackExportItemDO::getId));
    }
    List<InspectionFeedbackRow> selectFrozenRows(@Param("query") InspectionFeedbackExportRows query);
}
