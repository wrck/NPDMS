package cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectExceptionCloseSnapshotQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectGovernanceHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectStageSnapshotSequenceQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectStageSnapshotMapper {

    /** 仅供Repository在动作规则校验后追加；本Mapper不暴露更新或删除能力。 */
    int insertAppendOnly(ProjectStageSnapshotDO snapshot);

    Integer selectNextSnapshotNo(@Param("query") ProjectStageSnapshotSequenceQuery query);

    default PageResult<ProjectStageSnapshotDO> selectGovernanceHistoryPage(
            ProjectGovernanceHistoryPageQuery query) {
        long total = selectGovernanceHistoryCount(query);
        return total == 0 ? PageResult.empty()
                : new PageResult<>(selectGovernanceHistoryList(query), total);
    }

    long selectGovernanceHistoryCount(@Param("query") ProjectGovernanceHistoryPageQuery query);

    List<ProjectStageSnapshotDO> selectGovernanceHistoryList(
            @Param("query") ProjectGovernanceHistoryPageQuery query);

    ProjectStageSnapshotDO selectLatestReusableExceptionClose(
            @Param("query") ProjectExceptionCloseSnapshotQuery query);
}
