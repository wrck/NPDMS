package cn.iocoder.yudao.module.pms.project.dal.mysql.schedulebackward;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward.ScheduleBackwardItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 工期倒排阶段明细 Mapper（FR-PROJ-018）。
 */
@Mapper
public interface ScheduleBackwardItemMapper extends BaseMapperX<ScheduleBackwardItemDO> {

    default List<ScheduleBackwardItemDO> selectListByBackwardId(Long backwardId) {
        return selectList(new LambdaQueryWrapperX<ScheduleBackwardItemDO>()
                .eq(ScheduleBackwardItemDO::getBackwardId, backwardId)
                .orderByAsc(ScheduleBackwardItemDO::getSort)
                .orderByAsc(ScheduleBackwardItemDO::getId));
    }

    default int deleteByBackwardId(Long backwardId) {
        return delete(new LambdaQueryWrapperX<ScheduleBackwardItemDO>()
                .eq(ScheduleBackwardItemDO::getBackwardId, backwardId));
    }

}
