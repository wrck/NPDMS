package cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.vo.ProjectClosurePageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectClosureMapper extends BaseMapperX<ProjectClosureDO> {

    default ProjectClosureDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<ProjectClosureDO>()
                .eq(ProjectClosureDO::getProjectId, projectId)
                .eq(ProjectClosureDO::getCode, code));
    }

    default PageResult<ProjectClosureDO> selectPage(ProjectClosurePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectClosureDO>()
                .eqIfPresent(ProjectClosureDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ProjectClosureDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectClosureDO::getName, reqVO.getName())
                .eqIfPresent(ProjectClosureDO::getClosureType, reqVO.getClosureType())
                .eqIfPresent(ProjectClosureDO::getStatus, reqVO.getStatus())
                .orderByDesc(ProjectClosureDO::getId));
    }

}
