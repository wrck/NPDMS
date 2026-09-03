package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectWorkBindingFactLookupQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTemplateRevisionFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectSatisfactionTaskFactLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectSatisfactionTaskProjectLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 既有ProjectTask ExecutionContract的受控WorkBinding场景化查询。 */
@Mapper
public interface ProjectWorkBindingFactMapper {

    List<ProjectWorkBindingFactRecord> selectCurrentFacts(
            @Param("query") ProjectWorkBindingFactLookupQuery query);

    ProjectTaskInstanceDO selectProjectTaskForUpdate(
            @Param("query") ProjectWorkBindingFactLockQuery query);

    ProjectTaskExecutionContractDO selectCurrentContractForUpdate(
            @Param("query") ProjectWorkBindingFactLockQuery query);

    ProjectTemplateRevisionFactRecord selectTemplateRevisionFact(
            @Param("query") ProjectTemplateRevisionFactQuery query);

    List<ProjectSatisfactionTaskFactRecord> selectSatisfactionTaskForUpdate(
            @Param("query") ProjectSatisfactionTaskFactLockQuery query);

    List<ProjectSatisfactionTaskFactRecord> selectProjectSatisfactionTaskForUpdate(
            @Param("query") ProjectSatisfactionTaskProjectLockQuery query);
}
