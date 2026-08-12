package cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialrequisition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition.MaterialRequisitionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaterialRequisitionMapper extends BaseMapperX<MaterialRequisitionDO> {

    default PageResult<MaterialRequisitionDO> selectPage(MaterialRequisitionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MaterialRequisitionDO>()
                .eqIfPresent(MaterialRequisitionDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(MaterialRequisitionDO::getCode, reqVO.getCode())
                .likeIfPresent(MaterialRequisitionDO::getName, reqVO.getName())
                .eqIfPresent(MaterialRequisitionDO::getRequisitionType, reqVO.getRequisitionType())
                .eqIfPresent(MaterialRequisitionDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(MaterialRequisitionDO::getStockStatus, reqVO.getStockStatus())
                .eqIfPresent(MaterialRequisitionDO::getStatus, reqVO.getStatus())
                .eqIfPresent(MaterialRequisitionDO::getApplicantUserId, reqVO.getApplicantUserId())
                .eqIfPresent(MaterialRequisitionDO::getTriggerSource, reqVO.getTriggerSource())
                .betweenIfPresent(MaterialRequisitionDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MaterialRequisitionDO::getId));
    }

    /**
     * 按单号查询，用于全局唯一性校验
     */
    default MaterialRequisitionDO selectByCode(String code) {
        return selectOne(MaterialRequisitionDO::getCode, code);
    }

    /**
     * 按触发来源与触发来源关联编号查询
     */
    default List<MaterialRequisitionDO> selectListByTriggerSource(String triggerSource, Long triggerRefId) {
        return selectList(new LambdaQueryWrapperX<MaterialRequisitionDO>()
                .eq(MaterialRequisitionDO::getTriggerSource, triggerSource)
                .eq(MaterialRequisitionDO::getTriggerRefId, triggerRefId));
    }

}
