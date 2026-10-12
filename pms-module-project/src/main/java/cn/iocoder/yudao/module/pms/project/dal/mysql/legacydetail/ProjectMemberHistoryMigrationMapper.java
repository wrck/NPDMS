package cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/** PROJ拥有的历史成员写入；显式审计字段不经过普通自动填充。 */
@Mapper
public interface ProjectMemberHistoryMigrationMapper {
    /** 非实体参数，避免MyBatis在显式历史SQL前自动填充原NULL审计字段。 */
    record HistoricalInsert(ProjectMemberAssignmentDO values) { }
    record ProjectQuery(Long tenantId, Long projectId) { }
    record CloseInterval(Long tenantId, Long projectId, Long memberId, Integer expectedVersion,
                         LocalDateTime effectiveTo, String actor, String reason) { }
    record PrimaryUpdate(Long tenantId, Long projectId, Long expectedVersion, String expectedLifecycle,
                         Long managerId, String managerName, String employeeNo, String assignmentStatus) { }
    List<ProjectMemberAssignmentDO> selectProjectForUpdate(@Param("query") ProjectQuery query);
    int insertHistorical(@Param("entry") HistoricalInsert entry);
    int closeApprovedInterval(@Param("query") CloseInterval query);
    int updateApprovedPrimary(@Param("query") PrimaryUpdate query);
}
