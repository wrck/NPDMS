package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLookupQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 既有ProjectTask ExecutionContract的PRE-02场景化查询。 */
@Mapper
public interface ProjectWorkBindingFactMapper {

    List<ProjectWorkBindingFactRecord> selectCurrentFacts(
            @Param("query") ProjectWorkBindingFactLookupQuery query);

    ProjectTaskInstanceDO selectProjectTaskForUpdate(
            @Param("query") ProjectWorkBindingFactLockQuery query);

    ProjectTaskExecutionContractDO selectCurrentContractForUpdate(
            @Param("query") ProjectWorkBindingFactLockQuery query);
}
