package cn.iocoder.yudao.module.pms.project.dal.mysql.batchchange;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 团队批量变更明细 Mapper（FR-PROJ-014）。
 */
@Mapper
public interface TeamBatchChangeItemMapper extends BaseMapperX<TeamBatchChangeItemDO> {

    default List<TeamBatchChangeItemDO> selectListByBatchId(Long batchId) {
        return selectList(new LambdaQueryWrapperX<TeamBatchChangeItemDO>()
                .eq(TeamBatchChangeItemDO::getBatchId, batchId)
                .orderByAsc(TeamBatchChangeItemDO::getId));
    }

    default int deleteByBatchId(Long batchId) {
        return delete(new LambdaQueryWrapperX<TeamBatchChangeItemDO>()
                .eq(TeamBatchChangeItemDO::getBatchId, batchId));
    }

}
