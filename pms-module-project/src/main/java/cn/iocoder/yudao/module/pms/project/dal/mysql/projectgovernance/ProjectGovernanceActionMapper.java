package cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernancePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectGovernanceActionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 项目治理动作 Mapper（FR-PROJ-022 / T-V2-PROJ-003）
 */
@Mapper
public interface ProjectGovernanceActionMapper extends BaseMapperX<ProjectGovernanceActionDO> {

    default ProjectGovernanceActionDO selectByActionNo(String actionNo) {
        return selectOne(new LambdaQueryWrapperX<ProjectGovernanceActionDO>()
                .eq(ProjectGovernanceActionDO::getActionNo, actionNo));
    }

    default PageResult<ProjectGovernanceActionDO> selectPage(ProjectGovernancePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectGovernanceActionDO>()
                .eqIfPresent(ProjectGovernanceActionDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(ProjectGovernanceActionDO::getActionNo, reqVO.getActionNo())
                .eqIfPresent(ProjectGovernanceActionDO::getActionType, reqVO.getActionType())
                .eqIfPresent(ProjectGovernanceActionDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectGovernanceActionDO::getApplicantUserId, reqVO.getApplicantUserId())
                .orderByDesc(ProjectGovernanceActionDO::getId));
    }

}
