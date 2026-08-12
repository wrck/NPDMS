package cn.iocoder.yudao.module.pms.project.dal.mysql.schedulebackward;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.schedulebackward.vo.ScheduleBackwardPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.schedulebackward.ScheduleBackwardDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 工期倒排记录 Mapper（FR-PROJ-018）。
 */
@Mapper
public interface ScheduleBackwardMapper extends BaseMapperX<ScheduleBackwardDO> {

    default PageResult<ScheduleBackwardDO> selectPage(ScheduleBackwardPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ScheduleBackwardDO>()
                .eqIfPresent(ScheduleBackwardDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(ScheduleBackwardDO::getProjectType, reqVO.getProjectType())
                .eqIfPresent(ScheduleBackwardDO::getStatus, reqVO.getStatus())
                .orderByDesc(ScheduleBackwardDO::getId));
    }

}
