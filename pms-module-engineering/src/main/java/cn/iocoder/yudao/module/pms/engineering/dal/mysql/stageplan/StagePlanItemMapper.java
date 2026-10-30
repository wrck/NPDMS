package cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 阶段施工计划明细 Mapper（PLN-01/04）
 */
@Mapper
public interface StagePlanItemMapper extends BaseMapperX<StagePlanItemDO> {

    default List<StagePlanItemDO> selectListByBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapperX<StagePlanItemDO>()
                .eq(StagePlanItemDO::getBatchId, batchId)
                .orderByAsc(StagePlanItemDO::getSort, StagePlanItemDO::getId));
    }

    default int deleteByBatchId(Long batchId) {
        return delete(new LambdaQueryWrapperX<StagePlanItemDO>()
                .eq(StagePlanItemDO::getBatchId, batchId));
    }

}
