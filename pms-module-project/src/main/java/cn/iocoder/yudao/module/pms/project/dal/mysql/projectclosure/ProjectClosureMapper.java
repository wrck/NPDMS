package cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query.ProjectClosurePageQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectclosure.ProjectClosureDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectclosure.query.ProjectClosureGuardListQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectClosureMapper extends BaseMapperX<ProjectClosureDO> {

    List<ProjectClosureDO> selectListForClosureGuard(
            @Param("query") ProjectClosureGuardListQuery query);

    default PageResult<ProjectClosureDO> selectPage(ProjectClosurePageQuery reqVO) {
        if (reqVO.getVisibleProjectIds() == null || reqVO.getVisibleProjectIds().isEmpty()) return PageResult.empty();
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectClosureDO>()
                .in(ProjectClosureDO::getProjectId, reqVO.getVisibleProjectIds())
                .eqIfPresent(ProjectClosureDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ProjectClosureDO::getCode, reqVO.getCode())
                .likeIfPresent(ProjectClosureDO::getName, reqVO.getName())
                .eqIfPresent(ProjectClosureDO::getClosureType, reqVO.getClosureType())
                .eqIfPresent(ProjectClosureDO::getStatus, reqVO.getStatus())
                .orderByDesc(ProjectClosureDO::getId));
    }

}
