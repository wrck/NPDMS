package cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeliverableMapper extends BaseMapperX<DeliverableDO> {

    default PageResult<DeliverableDO> selectPage(DeliverablePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeliverableDO>()
                .eqIfPresent(DeliverableDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(DeliverableDO::getPhaseId, reqVO.getPhaseId())
                .likeIfPresent(DeliverableDO::getCode, reqVO.getCode())
                .likeIfPresent(DeliverableDO::getName, reqVO.getName())
                .eqIfPresent(DeliverableDO::getDeliverableType, reqVO.getDeliverableType())
                .eqIfPresent(DeliverableDO::getSourceType, reqVO.getSourceType())
                .eqIfPresent(DeliverableDO::getSourceId, reqVO.getSourceId())
                .eqIfPresent(DeliverableDO::getStatus, reqVO.getStatus())
                .orderByDesc(DeliverableDO::getId));
    }

    default DeliverableDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(DeliverableDO::getProjectId, projectId, DeliverableDO::getCode, code);
    }

}
