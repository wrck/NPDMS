package cn.iocoder.yudao.module.pms.service.dal.mysql.srvexecution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvexecution.SrvExecutionRetiredDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@Deprecated
public interface SrvExecutionMapper extends BaseMapperX<SrvExecutionRetiredDO> {

    default SrvExecutionRetiredDO selectByTaskIdAndCodeRetired(Long taskId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvExecutionRetiredDO>()
                .eq(SrvExecutionRetiredDO::getTaskId, taskId)
                .eq(SrvExecutionRetiredDO::getCode, code));
    }

    default PageResult<SrvExecutionRetiredDO> selectPageRetired(SrvExecutionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvExecutionRetiredDO>()
                .eqIfPresent(SrvExecutionRetiredDO::getTaskId, reqVO.getTaskId())
                .likeIfPresent(SrvExecutionRetiredDO::getCode, reqVO.getCode())
                .eqIfPresent(SrvExecutionRetiredDO::getRuleId, reqVO.getRuleId())
                .eqIfPresent(SrvExecutionRetiredDO::getExecutorUserId, reqVO.getExecutorUserId())
                .eqIfPresent(SrvExecutionRetiredDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SrvExecutionRetiredDO::getExecutionTime, reqVO.getExecutionTime())
                .orderByDesc(SrvExecutionRetiredDO::getId));
    }

}
