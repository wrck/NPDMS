package cn.iocoder.yudao.module.pms.project.dal.mysql.deliverablechecklist;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeliverableChecklistMapper extends BaseMapperX<DeliverableChecklistDO> {

    default DeliverableChecklistDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eq(DeliverableChecklistDO::getProjectId, projectId)
                .eq(DeliverableChecklistDO::getCode, code));
    }

    default PageResult<DeliverableChecklistDO> selectPage(DeliverableChecklistPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eqIfPresent(DeliverableChecklistDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(DeliverableChecklistDO::getCode, reqVO.getCode())
                .likeIfPresent(DeliverableChecklistDO::getName, reqVO.getName())
                .eqIfPresent(DeliverableChecklistDO::getAcceptanceId, reqVO.getAcceptanceId())
                .eqIfPresent(DeliverableChecklistDO::getDeliverableType, reqVO.getDeliverableType())
                .eqIfPresent(DeliverableChecklistDO::getStatus, reqVO.getStatus())
                .orderByDesc(DeliverableChecklistDO::getId));
    }

    /**
     * 查询某验收下指定类型的交付件列表（FR-ACC-005 门禁校验数据源）
     */
    default List<DeliverableChecklistDO> selectListByAcceptanceIdAndType(Long acceptanceId, String deliverableType) {
        return selectList(new LambdaQueryWrapperX<DeliverableChecklistDO>()
                .eq(DeliverableChecklistDO::getAcceptanceId, acceptanceId)
                .eq(DeliverableChecklistDO::getDeliverableType, deliverableType));
    }

}
