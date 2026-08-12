package cn.iocoder.yudao.module.pms.project.dal.mysql.batchchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.batchchange.vo.TeamBatchChangePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.batchchange.TeamBatchChangeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 团队批量变更批次 Mapper（FR-PROJ-014）。
 */
@Mapper
public interface TeamBatchChangeMapper extends BaseMapperX<TeamBatchChangeDO> {

    default PageResult<TeamBatchChangeDO> selectPage(TeamBatchChangePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TeamBatchChangeDO>()
                .eqIfPresent(TeamBatchChangeDO::getBatchNo, reqVO.getBatchNo())
                .eqIfPresent(TeamBatchChangeDO::getSourceUserId, reqVO.getSourceUserId())
                .eqIfPresent(TeamBatchChangeDO::getTargetUserId, reqVO.getTargetUserId())
                .eqIfPresent(TeamBatchChangeDO::getScopeType, reqVO.getScopeType())
                .eqIfPresent(TeamBatchChangeDO::getStatus, reqVO.getStatus())
                .orderByDesc(TeamBatchChangeDO::getId));
    }

    default TeamBatchChangeDO selectByBatchNo(String batchNo) {
        return selectOne(new LambdaQueryWrapperX<TeamBatchChangeDO>()
                .eq(TeamBatchChangeDO::getBatchNo, batchNo));
    }

}
