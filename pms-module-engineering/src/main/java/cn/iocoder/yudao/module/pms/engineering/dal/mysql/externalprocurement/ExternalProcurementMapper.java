package cn.iocoder.yudao.module.pms.engineering.dal.mysql.externalprocurement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.externalprocurement.ExternalProcurementDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ExternalProcurementMapper extends BaseMapperX<ExternalProcurementDO> {

    default PageResult<ExternalProcurementDO> selectPage(ExternalProcurementPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ExternalProcurementDO>()
                .eqIfPresent(ExternalProcurementDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ExternalProcurementDO::getCode, reqVO.getCode())
                .likeIfPresent(ExternalProcurementDO::getName, reqVO.getName())
                .eqIfPresent(ExternalProcurementDO::getProcurementType, reqVO.getProcurementType())
                .eqIfPresent(ExternalProcurementDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ExternalProcurementDO::getApplicantUserId, reqVO.getApplicantUserId())
                .eqIfPresent(ExternalProcurementDO::getTriggerSource, reqVO.getTriggerSource())
                .betweenIfPresent(ExternalProcurementDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ExternalProcurementDO::getId));
    }

    /**
     * 按单号查询，用于全局唯一性校验
     */
    default ExternalProcurementDO selectByCode(String code) {
        return selectOne(ExternalProcurementDO::getCode, code);
    }

    /**
     * 按触发来源与触发来源关联编号查询
     */
    default List<ExternalProcurementDO> selectListByTriggerSource(String triggerSource, Long triggerRefId) {
        return selectList(new LambdaQueryWrapperX<ExternalProcurementDO>()
                .eq(ExternalProcurementDO::getTriggerSource, triggerSource)
                .eq(ExternalProcurementDO::getTriggerRefId, triggerRefId));
    }

}
