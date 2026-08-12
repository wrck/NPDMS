package cn.iocoder.yudao.module.pms.cutover.dal.mysql.execution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo.CutExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.execution.CutExecutionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 割接执行 Mapper。
 */
@Mapper
public interface CutExecutionMapper extends BaseMapperX<CutExecutionDO> {

    default PageResult<CutExecutionDO> selectPage(CutExecutionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CutExecutionDO>()
                .eqIfPresent(CutExecutionDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(CutExecutionDO::getCode, reqVO.getCode())
                .likeIfPresent(CutExecutionDO::getStepName, reqVO.getStepName())
                .eqIfPresent(CutExecutionDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(CutExecutionDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CutExecutionDO::getId));
    }

    default CutExecutionDO selectByTaskCode(Long taskId, String code) {
        return selectOne(CutExecutionDO::getTaskId, taskId, CutExecutionDO::getCode, code);
    }

    default List<CutExecutionDO> selectListByTask(Long taskId) {
        return selectList(new LambdaQueryWrapperX<CutExecutionDO>()
                .eq(CutExecutionDO::getTaskId, taskId)
                .orderByDesc(CutExecutionDO::getId));
    }

    default Long selectCountByTaskFailedNotClosed(Long taskId) {
        return selectCount(new LambdaQueryWrapperX<CutExecutionDO>()
                .eq(CutExecutionDO::getTaskId, taskId)
                .eq(CutExecutionDO::getStatus, 3));
    }
}
