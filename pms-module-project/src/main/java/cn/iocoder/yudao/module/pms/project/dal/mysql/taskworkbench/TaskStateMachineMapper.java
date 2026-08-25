package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachineRevisionLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateTransitionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目任务状态机版本 Mapper。
 */
@Mapper
public interface TaskStateMachineMapper {

    int insertDraft(@Param("revision") TaskStateMachineRevisionDO revision);

    TaskStateMachineRevisionDO selectCurrentPublished(@Param("query") TaskStateMachinePublishedQuery query);

    default TaskStateMachineDefinition selectPublished(TaskStateMachinePublishedQuery query) {
        TaskStateMachineRevisionDO revision = selectCurrentPublished(query);
        return revision == null ? null : new TaskStateMachineDefinition(
                revision, selectTransitions(new TaskStateMachineRevisionLockQuery(
                query.getTenantId(), revision.getId())));
    }

    TaskStateMachineRevisionDO selectRevisionForUpdate(
            @Param("query") TaskStateMachineRevisionLockQuery query);

    List<TaskStateTransitionDO> selectTransitions(
            @Param("query") TaskStateMachineRevisionLockQuery query);

    List<TaskStateTransitionDO> selectTransitionsForUpdate(
            @Param("query") TaskStateMachineRevisionLockQuery query);

    TaskStateTransitionDO selectTransition(@Param("query") TaskStateTransitionQuery query);

    default TaskStateTransitionDO requireTransition(TaskStateTransitionQuery query) {
        TaskStateTransitionDO transition = selectTransition(query);
        if (transition == null) {
            throw new IllegalArgumentException("未知任务状态或动作");
        }
        return transition;
    }

    int publishRevisionIfMatch(@Param("query") TaskStateMachinePublishUpdate update);

    default int publishIfValid(TaskStateMachinePublishUpdate update) {
        TaskStateMachineRevisionLockQuery lockQuery = new TaskStateMachineRevisionLockQuery(
                update.tenantId(), update.revisionId());
        TaskStateMachineRevisionDO revision = selectRevisionForUpdate(lockQuery);
        if (revision == null || !"DRAFT".equals(revision.getStatus())
                || !update.expectedVersion().equals(revision.getVersion())) {
            return 0;
        }
        new TaskStateMachineDefinition(revision, selectTransitionsForUpdate(lockQuery)).validateForPublish();
        return publishRevisionIfMatch(update);
    }
}
