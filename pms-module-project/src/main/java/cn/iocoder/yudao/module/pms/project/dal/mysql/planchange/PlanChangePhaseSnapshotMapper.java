package cn.iocoder.yudao.module.pms.project.dal.mysql.planchange;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.planchange.PlanChangePhaseSnapshotRetiredDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 计划变更阶段快照 Mapper
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Mapper
@Deprecated
public interface PlanChangePhaseSnapshotMapper extends BaseMapperX<PlanChangePhaseSnapshotRetiredDO> {

    default List<PlanChangePhaseSnapshotRetiredDO> selectListByChangeRequestIdRetired(Long changeRequestId) {
        return selectList(new LambdaQueryWrapperX<PlanChangePhaseSnapshotRetiredDO>()
                .eq(PlanChangePhaseSnapshotRetiredDO::getChangeRequestId, changeRequestId));
    }

    default int deleteByChangeRequestIdRetired(Long changeRequestId) {
        return delete(new LambdaQueryWrapperX<PlanChangePhaseSnapshotRetiredDO>()
                .eq(PlanChangePhaseSnapshotRetiredDO::getChangeRequestId, changeRequestId));
    }

}
