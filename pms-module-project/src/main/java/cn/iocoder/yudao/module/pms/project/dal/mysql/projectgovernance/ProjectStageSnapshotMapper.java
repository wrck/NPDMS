package cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectExceptionCloseSnapshotQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectGovernanceHistoryPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectStageSnapshotMapper extends BaseMapperX<ProjectStageSnapshotDO> {

    default PageResult<ProjectStageSnapshotDO> selectGovernanceHistoryPage(
            ProjectGovernanceHistoryPageQuery query) {
        long total = selectGovernanceHistoryCount(query);
        return total == 0 ? PageResult.empty()
                : new PageResult<>(selectGovernanceHistoryList(query), total);
    }

    long selectGovernanceHistoryCount(@Param("query") ProjectGovernanceHistoryPageQuery query);

    List<ProjectStageSnapshotDO> selectGovernanceHistoryList(
            @Param("query") ProjectGovernanceHistoryPageQuery query);

    ProjectStageSnapshotDO selectLatestReusableExceptionCloseForUpdate(
            @Param("query") ProjectExceptionCloseSnapshotQuery query);
}
