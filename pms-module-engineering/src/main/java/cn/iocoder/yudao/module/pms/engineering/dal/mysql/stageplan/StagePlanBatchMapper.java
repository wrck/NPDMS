package cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 阶段施工计划批次 Mapper（PLN-01/04）
 */
@Mapper
public interface StagePlanBatchMapper extends BaseMapperX<StagePlanBatchDO> {

    default PageResult<StagePlanBatchDO> selectPage(cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.query.StagePlanPageQuery reqVO) {
        if (reqVO.getVisibleProjectIds() == null || reqVO.getVisibleProjectIds().isEmpty()) return new PageResult<>(List.of(), 0L);
        return selectPage(reqVO, new LambdaQueryWrapperX<StagePlanBatchDO>()
                .in(StagePlanBatchDO::getProjectId, reqVO.getVisibleProjectIds())
                .eqIfPresent(StagePlanBatchDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(StagePlanBatchDO::getStatus, reqVO.getStatus())
                .orderByDesc(StagePlanBatchDO::getId));
    }

    default List<StagePlanBatchDO> selectListByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<StagePlanBatchDO>()
                .eq(StagePlanBatchDO::getProjectId, projectId)
                .orderByDesc(StagePlanBatchDO::getId));
    }

    default StagePlanBatchDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(new LambdaQueryWrapperX<StagePlanBatchDO>()
                .eq(StagePlanBatchDO::getBpmProcessInstanceId, processInstanceId));
    }

    default List<StagePlanBatchDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<StagePlanBatchDO>()
                .eq(StagePlanBatchDO::getStatus, status)
                .orderByDesc(StagePlanBatchDO::getId));
    }

}
