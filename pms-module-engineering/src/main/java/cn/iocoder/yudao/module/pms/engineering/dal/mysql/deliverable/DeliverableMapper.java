package cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

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

    /** 按来源业务键幂等查询（自动归档防重复；来源键项目内唯一）。 */
    default DeliverableDO selectByProjectAndSource(Long projectId, String sourceType, Long sourceId) {
        return selectOne(new LambdaQueryWrapperX<DeliverableDO>()
                .eq(DeliverableDO::getProjectId, projectId)
                .eq(DeliverableDO::getSourceType, sourceType)
                .eq(DeliverableDO::getSourceId, sourceId));
    }

    default List<DeliverableDO> selectListByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<DeliverableDO>()
                .eq(DeliverableDO::getProjectId, projectId)
                .orderByDesc(DeliverableDO::getArchivedTime)
                .orderByDesc(DeliverableDO::getId));
    }

}
