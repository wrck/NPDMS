package cn.iocoder.yudao.module.pms.project.dal.mysql.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.risk.ProjectRiskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * PMS 项目风险 Mapper（FR-PROJ-026 / T-V1-PROJ-009）。
 */
@Mapper
public interface ProjectRiskMapper extends BaseMapperX<ProjectRiskDO> {

    default PageResult<ProjectRiskDO> selectPage(ProjectRiskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectRiskDO>()
                .eqIfPresent(ProjectRiskDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ProjectRiskDO::getTitle, reqVO.getTitle())
                .eqIfPresent(ProjectRiskDO::getRiskLevel, reqVO.getRiskLevel())
                .eqIfPresent(ProjectRiskDO::getRiskType, reqVO.getRiskType())
                .eqIfPresent(ProjectRiskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectRiskDO::getOwnerUserId, reqVO.getOwnerUserId())
                .orderByDesc(ProjectRiskDO::getId));
    }

    /**
     * 查询项目下全部风险。
     */
    default List<ProjectRiskDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectRiskDO>()
                .eq(ProjectRiskDO::getProjectId, projectId)
                .orderByDesc(ProjectRiskDO::getId));
    }

    /**
     * 统计项目下指定状态风险数。
     */
    default Long selectCountByProjectAndStatus(Long projectId, Integer status) {
        return selectCount(new LambdaQueryWrapperX<ProjectRiskDO>()
                .eq(ProjectRiskDO::getProjectId, projectId)
                .eq(ProjectRiskDO::getStatus, status));
    }
}
