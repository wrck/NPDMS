package cn.iocoder.yudao.module.pms.cutover.dal.mysql.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.risk.CutRiskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 割接风险 Mapper。
 */
@Mapper
public interface CutRiskMapper extends BaseMapperX<CutRiskDO> {

    default PageResult<CutRiskDO> selectPage(CutRiskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CutRiskDO>()
                .eqIfPresent(CutRiskDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(CutRiskDO::getCode, reqVO.getCode())
                .likeIfPresent(CutRiskDO::getName, reqVO.getName())
                .eqIfPresent(CutRiskDO::getRiskType, reqVO.getRiskType())
                .eqIfPresent(CutRiskDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CutRiskDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CutRiskDO::getId));
    }

    default CutRiskDO selectByTaskCode(Long taskId, String code) {
        return selectOne(CutRiskDO::getTaskId, taskId, CutRiskDO::getCode, code);
    }

    default List<CutRiskDO> selectListByTask(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CutRiskDO>()
                .eq(CutRiskDO::getTaskId, taskId)
                .orderByDesc(CutRiskDO::getId));
    }

    default Long selectCountByTaskNotClosed(Long taskId) {
        return selectCount(new LambdaQueryWrapperX<CutRiskDO>()
                .eq(CutRiskDO::getTaskId, taskId)
                .in(CutRiskDO::getStatus, 0, 1, 3));
    }
}
