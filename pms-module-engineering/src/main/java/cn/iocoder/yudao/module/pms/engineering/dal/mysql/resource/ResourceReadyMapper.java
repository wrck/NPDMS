package cn.iocoder.yudao.module.pms.engineering.dal.mysql.resource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo.ResourceReadyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.resource.ResourceReadyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResourceReadyMapper extends BaseMapperX<ResourceReadyDO> {

    default PageResult<ResourceReadyDO> selectPage(ResourceReadyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ResourceReadyDO>()
                .eqIfPresent(ResourceReadyDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ResourceReadyDO::getCode, reqVO.getCode())
                .likeIfPresent(ResourceReadyDO::getName, reqVO.getName())
                .eqIfPresent(ResourceReadyDO::getResourceType, reqVO.getResourceType())
                .eqIfPresent(ResourceReadyDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(ResourceReadyDO::getReadyStatus, reqVO.getReadyStatus())
                .orderByDesc(ResourceReadyDO::getId));
    }

    default ResourceReadyDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(ResourceReadyDO::getProjectId, projectId, ResourceReadyDO::getCode, code);
    }

    /**
     * 统计项目下未就绪（ready_status != 1）的资源数量，用于实施动作门禁校验。
     */
    default Long selectCountByProjectNotReady(Long projectId) {
        return selectCount(new LambdaQueryWrapperX<ResourceReadyDO>()
                .eq(ResourceReadyDO::getProjectId, projectId)
                .ne(ResourceReadyDO::getReadyStatus, 1));
    }

}
