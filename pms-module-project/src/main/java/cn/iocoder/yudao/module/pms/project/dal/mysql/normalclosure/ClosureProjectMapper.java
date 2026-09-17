package cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.NormalClosureExitRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 闭环链路对 PROJ 表的受控访问：任务/成员锁、模板修订与项目终态写入。仅闭环实现方使用，无通用 CRUD。 */
@Mapper
public interface ClosureProjectMapper {

    record ProjectQuery(Long tenantId, Long projectId) {
        public java.util.Set<String> getServiceRoleCodes() {
            return cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.SERVICE_CODES;
        }
    }

    record TemplateRevisionQuery(Long tenantId, Long templateId, Integer revisionNo) {}

    record ExitUpdate(Long tenantId, Long projectId, Integer expectedVersion, String fromStage,
                      LocalDateTime closedAt, String updater) {}

    Long selectFrozenTemplateRevisionId(@Param("query") TemplateRevisionQuery query);

    List<ProjectTaskInstanceDO> selectTasks(@Param("query") ProjectQuery query);

    List<ProjectTaskInstanceDO> selectTasksForUpdate(@Param("query") ProjectQuery query);

    List<ProjectMemberAssignmentDO> selectPrimaryServiceManagersForUpdate(@Param("query") ProjectQuery query);

    int insertExitRecord(NormalClosureExitRecordDO row);

    int closeProjectIfMatch(@Param("query") ExitUpdate query);
}
