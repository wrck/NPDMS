package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionParsePartDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionExecutionRows;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface InspectionParsePartMapper extends BaseMapperX<InspectionParsePartDO> {
    default List<InspectionParsePartDO> selectForExecution(InspectionExecutionRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionParsePartDO>()
                .eq(InspectionParsePartDO::getTenantId, query.tenantId())
                .eq(InspectionParsePartDO::getExecutionId, query.executionId())
                .orderByAsc(InspectionParsePartDO::getPartNo));
    }
}
