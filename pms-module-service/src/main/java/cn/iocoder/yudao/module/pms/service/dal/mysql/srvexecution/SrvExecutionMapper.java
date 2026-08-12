package cn.iocoder.yudao.module.pms.service.dal.mysql.srvexecution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvexecution.SrvExecutionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SrvExecutionMapper extends BaseMapperX<SrvExecutionDO> {

    default SrvExecutionDO selectByTaskIdAndCode(Long taskId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvExecutionDO>()
                .eq(SrvExecutionDO::getTaskId, taskId)
                .eq(SrvExecutionDO::getCode, code));
    }

    default PageResult<SrvExecutionDO> selectPage(SrvExecutionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvExecutionDO>()
                .eqIfPresent(SrvExecutionDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(SrvExecutionDO::getCode, reqVO.getCode())
                .eqIfPresent(SrvExecutionDO::getRuleId, reqVO.getRuleId())
                .eqIfPresent(SrvExecutionDO::getExecutorUserId, reqVO.getExecutorUserId())
                .eqIfPresent(SrvExecutionDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SrvExecutionDO::getExecutionTime, reqVO.getExecutionTime())
                .orderByDesc(SrvExecutionDO::getId));
    }

}
