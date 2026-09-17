package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.normalclosure;

import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** ACC 闭环申请/快照/评审表访问。PROJ 表（任务/成员/模板/项目终态/退出记录）已归位 pms-module-project 的 ClosureProjectMapper。 */
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

    NormalClosureApplicationDO selectLatestApplication(@Param("query") ProjectQuery query);
    NormalClosureApplicationDO selectApplication(@Param("query") ApplicationQuery query);
    NormalClosureApplicationDO selectApplicationForUpdate(@Param("query") ApplicationQuery query);
    NormalClosureSnapshotDO selectLatestSnapshot(@Param("query") ProjectQuery query);
    NormalClosureSnapshotDO selectSnapshot(@Param("query") SnapshotQuery query);
    List<NormalClosureReviewDO> selectReviews(@Param("query") ApplicationQuery query);
    int insertSnapshot(NormalClosureSnapshotDO row);
    int insertApplication(NormalClosureApplicationDO row);
    int insertReview(NormalClosureReviewDO row);
    int decideApplicationIfMatch(@Param("query") ApplicationDecision query);
}
