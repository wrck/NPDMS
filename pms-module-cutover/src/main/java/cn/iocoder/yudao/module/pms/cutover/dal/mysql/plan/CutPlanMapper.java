package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutPlanDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 割接方案 Mapper。
 */
@Mapper
public interface CutPlanMapper extends BaseMapperX<CutPlanDO> {

    default PageResult<CutPlanDO> selectPage(CutPlanPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CutPlanDO>()
                .eqIfPresent(CutPlanDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(CutPlanDO::getCode, reqVO.getCode())
                .likeIfPresent(CutPlanDO::getName, reqVO.getName())
                .eqIfPresent(CutPlanDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CutPlanDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CutPlanDO::getId));
    }

    default CutPlanDO selectByTaskCode(Long taskId, String code) {
        return selectOne(CutPlanDO::getTaskId, taskId, CutPlanDO::getCode, code);
    }

    default List<CutPlanDO> selectListByTask(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CutPlanDO>()
                .eq(CutPlanDO::getTaskId, taskId)
                .orderByDesc(CutPlanDO::getId));
    }

    default Long selectCountByTaskApproved(Long taskId) {
        return selectCount(new LambdaQueryWrapperX<CutPlanDO>()
                .eq(CutPlanDO::getTaskId, taskId)
                .eq(CutPlanDO::getStatus, 2));
    }
}
