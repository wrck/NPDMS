package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionTaskMapper extends BaseMapperX<InspectionTaskDO> {
    InspectionTaskDO selectForUpdate(@Param("query") InspectionTaskRows query);
    List<InspectionTaskDO> selectPageList(@Param("query") InspectionTaskPageQuery query);
    long selectPageCount(@Param("query") InspectionTaskPageQuery query);
    List<cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionStateCount> selectStateCounts(@Param("query") InspectionTaskPageQuery query);
    List<InspectionTaskDO> selectForGovernance(@Param("query") InspectionProjectRows query);
}
