package cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskPageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask.SrvTaskRetiredDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask.query.InspectionGovernanceGuardQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
@Deprecated
public interface SrvTaskMapper extends BaseMapperX<SrvTaskRetiredDO> {

    default SrvTaskRetiredDO selectByProjectIdAndCodeRetired(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvTaskRetiredDO>()
                .eq(SrvTaskRetiredDO::getProjectId, projectId)
                .eq(SrvTaskRetiredDO::getCode, code));
    }

    default PageResult<SrvTaskRetiredDO> selectPageRetired(SrvTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvTaskRetiredDO>()
                .eqIfPresent(SrvTaskRetiredDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(SrvTaskRetiredDO::getEquipmentId, reqVO.getEquipmentId())
                .likeIfPresent(SrvTaskRetiredDO::getCode, reqVO.getCode())
                .likeIfPresent(SrvTaskRetiredDO::getName, reqVO.getName())
                .eqIfPresent(SrvTaskRetiredDO::getInspectionMode, reqVO.getInspectionMode())
                .eqIfPresent(SrvTaskRetiredDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(SrvTaskRetiredDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SrvTaskRetiredDO::getScheduledTime, reqVO.getScheduledTime())
                .betweenIfPresent(SrvTaskRetiredDO::getActualTime, reqVO.getActualTime())
                .orderByDesc(SrvTaskRetiredDO::getId));
    }

    default List<SrvTaskRetiredDO> selectListForGovernanceGuardRetired(InspectionGovernanceGuardQuery query) {
        if (query.projectIds().isEmpty()) {
            return List.of();
        }
        return selectListForGovernanceGuard0Retired(query);
    }

    List<SrvTaskRetiredDO> selectListForGovernanceGuard0Retired(@Param("query") InspectionGovernanceGuardQuery query);

}
