package cn.iocoder.yudao.module.pms.project.dal.mysql.maintenancetransition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo.MaintenanceTransitionPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.maintenancetransition.MaintenanceTransitionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaintenanceTransitionMapper extends BaseMapperX<MaintenanceTransitionDO> {

    default MaintenanceTransitionDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<MaintenanceTransitionDO>()
                .eq(MaintenanceTransitionDO::getProjectId, projectId)
                .eq(MaintenanceTransitionDO::getCode, code));
    }

    default PageResult<MaintenanceTransitionDO> selectPage(MaintenanceTransitionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MaintenanceTransitionDO>()
                .eqIfPresent(MaintenanceTransitionDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(MaintenanceTransitionDO::getCode, reqVO.getCode())
                .likeIfPresent(MaintenanceTransitionDO::getName, reqVO.getName())
                .eqIfPresent(MaintenanceTransitionDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(MaintenanceTransitionDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MaintenanceTransitionDO::getStartDate, reqVO.getStartDate())
                .orderByDesc(MaintenanceTransitionDO::getId));
    }

}
