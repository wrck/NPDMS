package cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/** Append-only evidence and state-machine-only application/project writes. Never exposes generic CRUD. */
@Mapper
public interface NormalClosureMapper {
    record ProjectQuery(Long tenantId, Long projectId) {}
    record ApplicationQuery(Long tenantId, Long projectId, Long applicationId) {}
    record ApplicationIdentityQuery(Long tenantId, Long applicationId) {}
    Long selectApplicationProjectId(@Param("query") ApplicationIdentityQuery query);
    record SnapshotQuery(Long tenantId, Long projectId, Long snapshotId) {}
    record ProcessQuery(Long tenantId, String processInstanceId) {}
    NormalClosureApplicationDO selectApplicationByProcess(@Param("query") ProcessQuery query);
    record ApplicationDecision(Long tenantId, Long projectId, Long applicationId, Integer expectedVersion,
                               String status, LocalDateTime decidedAt, String updater) {}
    record ExitUpdate(Long tenantId, Long projectId, Integer expectedVersion, String fromStage,
                      LocalDateTime closedAt, String updater) {}

    record TemplateRevisionQuery(Long tenantId, Long templateId, Integer revisionNo) {}
    Long selectFrozenTemplateRevisionId(@Param("query") TemplateRevisionQuery query);
    List<ProjectTaskInstanceDO> selectTasks(@Param("query") ProjectQuery query);
    List<ProjectTaskInstanceDO> selectTasksForUpdate(@Param("query") ProjectQuery query);
    List<ProjectMemberAssignmentDO> selectPrimaryServiceManagersForUpdate(@Param("query") ProjectQuery query);
    NormalClosureApplicationDO selectLatestApplication(@Param("query") ProjectQuery query);
    NormalClosureApplicationDO selectApplication(@Param("query") ApplicationQuery query);
    NormalClosureApplicationDO selectApplicationForUpdate(@Param("query") ApplicationQuery query);
    NormalClosureSnapshotDO selectLatestSnapshot(@Param("query") ProjectQuery query);
    NormalClosureSnapshotDO selectSnapshot(@Param("query") SnapshotQuery query);
    List<NormalClosureReviewDO> selectReviews(@Param("query") ApplicationQuery query);
    int insertSnapshot(NormalClosureSnapshotDO row);
    int insertApplication(NormalClosureApplicationDO row);
    int insertReview(NormalClosureReviewDO row);
    int insertExitRecord(NormalClosureExitRecordDO row);
    int decideApplicationIfMatch(@Param("query") ApplicationDecision query);
    int closeProjectIfMatch(@Param("query") ExitUpdate query);
}
