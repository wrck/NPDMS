package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.task.vo.CutTaskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.LegacyCutoverSourceRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * PMS 割接任务 Mapper。
 */
@Mapper
public interface CutTaskMapper extends BaseMapperX<CutTaskDO> {

    default PageResult<CutTaskDO> selectPage(CutTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CutTaskDO>()
                .eqIfPresent(CutTaskDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(CutTaskDO::getCode, reqVO.getCode())
                .likeIfPresent(CutTaskDO::getName, reqVO.getName())
                .eqIfPresent(CutTaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CutTaskDO::getRiskLevel, reqVO.getRiskLevel())
                .betweenIfPresent(CutTaskDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(CutTaskDO::getId));
    }

    default CutTaskDO selectByProjectCode(Long projectId, String code) {
        return selectOne(CutTaskDO::getProjectId, projectId, CutTaskDO::getCode, code);
    }

    default List<CutTaskDO> selectListByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<CutTaskDO>()
                .eq(CutTaskDO::getProjectId, projectId)
                .orderByDesc(CutTaskDO::getId));
    }

    default List<CutTaskDO> selectListForGovernanceGuard(CutoverGovernanceGuardQuery query) {
        if (query.projectIds().isEmpty()) {
            return List.of();
        }
        return selectListForGovernanceGuard0(query);
    }

    List<CutTaskDO> selectListForGovernanceGuard0(@Param("query") CutoverGovernanceGuardQuery query);

    CutTaskDO selectLegacySourceForUpdate(@Param("query") LegacyCutoverSourceRowQuery query);
}
