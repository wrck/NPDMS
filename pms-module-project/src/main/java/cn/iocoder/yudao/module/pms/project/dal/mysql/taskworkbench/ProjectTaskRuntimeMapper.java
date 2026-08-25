package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskMoveLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskStructureUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeVersionUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskVisibilityQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** V1.8当前项目任务树持久化入口。 */
@Mapper
public interface ProjectTaskRuntimeMapper extends BaseMapperX<ProjectTaskInstanceDO> {

    default List<ProjectTaskInstanceDO> selectTree(ProjectTaskTreeQuery query) {
        if (query == null || query.projectIds() == null || query.projectIds().isEmpty()
                || (query.visibilityQuery() == null
                    && (query.visibleTaskIds() == null || query.visibleTaskIds().isEmpty()))) {
            return List.of();
        }
        return selectTreeQuery(query);
    }

    List<ProjectTaskInstanceDO> selectTreeQuery(@Param("query") ProjectTaskTreeQuery query);

    ProjectTaskInstanceDO selectTask(@Param("query") TaskByIdQuery query);

    List<Long> selectVisibleTaskIds(@Param("query") TaskVisibilityQuery query);

    List<Long> selectFullTaskIds(@Param("query") TaskVisibilityQuery query);

    List<TaskStageCount> selectStageCounts(@Param("query") TaskVisibilityQuery query);

    default ProjectTaskMoveLocks selectMoveLocks(ProjectTaskMoveLockQuery query) {
        ProjectMasterDO project = selectProjectForUpdate(query);
        ProjectTaskInstanceDO source = selectSourceTaskForUpdate(query);
        ProjectTaskInstanceDO target = query.targetParentTaskId() == null
                ? null : selectTargetParentForUpdate(query);
        List<ProjectTaskInstanceDO> subtree = source == null
                ? List.of() : selectMovedSubtreeForUpdate(query);
        boolean targetInsideSubtree = target != null && source != null
                && (target.getId().equals(source.getId())
                || subtree.stream().anyMatch(task -> target.getId().equals(task.getId())));
        return new ProjectTaskMoveLocks(project, source, target, subtree, targetInsideSubtree);
    }

    ProjectMasterDO selectProjectForUpdate(@Param("query") ProjectTaskMoveLockQuery query);

    ProjectTaskInstanceDO selectSourceTaskForUpdate(@Param("query") ProjectTaskMoveLockQuery query);

    ProjectTaskInstanceDO selectTargetParentForUpdate(@Param("query") ProjectTaskMoveLockQuery query);

    List<ProjectTaskInstanceDO> selectMovedSubtreeForUpdate(@Param("query") ProjectTaskMoveLockQuery query);

    int updateStructureIfMatch(@Param("query") ProjectTaskStructureUpdate update);

    int incrementTaskTreeVersion(@Param("query") ProjectTaskTreeVersionUpdate update);

    default void rebuildMovedSubtreePaths(ProjectTaskStructureUpdate update) {
        deleteOldExternalPaths(update);
        insertNewExternalPaths(update);
        updateMovedSubtreeShape(update);
    }

    int deleteOldExternalPaths(@Param("query") ProjectTaskStructureUpdate update);

    int insertNewExternalPaths(@Param("query") ProjectTaskStructureUpdate update);

    int updateMovedSubtreeShape(@Param("query") ProjectTaskStructureUpdate update);

    record ProjectTaskMoveLocks(
            ProjectMasterDO project,
            ProjectTaskInstanceDO sourceTask,
            ProjectTaskInstanceDO targetParentTask,
            List<ProjectTaskInstanceDO> movedSubtree,
            boolean targetInsideMovedSubtree) {
    }
}
