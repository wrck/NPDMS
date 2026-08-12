package cn.iocoder.yudao.module.pms.project.dal.mysql.planchange;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangePhaseSnapshotDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 计划变更阶段快照 Mapper
 */
@Mapper
public interface PlanChangePhaseSnapshotMapper extends BaseMapperX<PlanChangePhaseSnapshotDO> {

    default List<PlanChangePhaseSnapshotDO> selectListByChangeRequestId(Long changeRequestId) {
        return selectList(new LambdaQueryWrapperX<PlanChangePhaseSnapshotDO>()
                .eq(PlanChangePhaseSnapshotDO::getChangeRequestId, changeRequestId));
    }

    default int deleteByChangeRequestId(Long changeRequestId) {
        return delete(new LambdaQueryWrapperX<PlanChangePhaseSnapshotDO>()
                .eq(PlanChangePhaseSnapshotDO::getChangeRequestId, changeRequestId));
    }

}
