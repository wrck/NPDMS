package cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskPageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask.SrvTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SrvTaskMapper extends BaseMapperX<SrvTaskDO> {

    default SrvTaskDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvTaskDO>()
                .eq(SrvTaskDO::getProjectId, projectId)
                .eq(SrvTaskDO::getCode, code));
    }

    default PageResult<SrvTaskDO> selectPage(SrvTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvTaskDO>()
                .eqIfPresent(SrvTaskDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(SrvTaskDO::getEquipmentId, reqVO.getEquipmentId())
                .likeIfPresent(SrvTaskDO::getCode, reqVO.getCode())
                .likeIfPresent(SrvTaskDO::getName, reqVO.getName())
                .eqIfPresent(SrvTaskDO::getInspectionMode, reqVO.getInspectionMode())
                .eqIfPresent(SrvTaskDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(SrvTaskDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SrvTaskDO::getScheduledTime, reqVO.getScheduledTime())
                .betweenIfPresent(SrvTaskDO::getActualTime, reqVO.getActualTime())
                .orderByDesc(SrvTaskDO::getId));
    }

}
