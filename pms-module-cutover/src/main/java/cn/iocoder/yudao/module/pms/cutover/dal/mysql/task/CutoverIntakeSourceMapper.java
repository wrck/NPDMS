package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverIntakeSourceQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CutoverIntakeSourceMapper extends BaseMapperX<CutoverTaskDO> {
    default CutoverTaskDO find(CutoverIntakeSourceQuery query) {
        if (query == null || query.tenantId() == null) throw new IllegalArgumentException("来源身份缺失");
        var wrapper = new LambdaQueryWrapperX<CutoverTaskDO>().eq(CutoverTaskDO::getTenantId, query.tenantId())
                .eq(CutoverTaskDO::getIntakeSourceType, query.sourceType());
        if ("ITR".equals(query.sourceType()) && query.sourceSystem() != null && query.sourceBusinessNo() != null)
            wrapper.eq(CutoverTaskDO::getSourceSystem, query.sourceSystem()).eq(CutoverTaskDO::getSourceBusinessNo, query.sourceBusinessNo());
        else if ("PROJECT_EVENT".equals(query.sourceType()) && query.businessEventId() != null)
            wrapper.eq(CutoverTaskDO::getBusinessEventId, query.businessEventId());
        else throw new IllegalArgumentException("来源身份缺失");
        return selectOne(wrapper);
    }
}
