package cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** Source mappings are CAS-updated. Snapshots and records only expose append/read methods. */
@Mapper
public interface ProjectLegacyDetailMapper {
    record SourceIdentityQuery(Long tenantId,String sourceProjectKey) {}
    record ProjectSourcesQuery(Long tenantId,Long projectId) {}
    record SnapshotIdentityQuery(Long tenantId,Long sourceId,String batchKey) {}
    record SnapshotQuery(Long tenantId,Long id) {}
    record RecordsPageQuery(Long tenantId,Long snapshotId,String domainCode,String sourceKey,
                            String parentDomain,String parentSourceKey,int offset,int limit) {}
    record SourceHeadUpdate(Long tenantId,Long id,Long expectedVersion,Long snapshotId) {}
    ProjectLegacySourceDO selectSourceForUpdate(@Param("query") SourceIdentityQuery query);
    List<ProjectLegacySourceDO> selectProjectSources(@Param("query") ProjectSourcesQuery query);
    ProjectLegacySnapshotDO selectSnapshot(@Param("query") SnapshotQuery query);
    ProjectLegacySnapshotDO selectBatch(@Param("query") SnapshotIdentityQuery query);
    int insertSource(ProjectLegacySourceDO row);
    int appendSnapshot(ProjectLegacySnapshotDO row);
    int appendRecord(ProjectLegacyRecordDO row);
    int updateHead(@Param("query") SourceHeadUpdate query);
    long countRecords(@Param("query") RecordsPageQuery query);
    List<ProjectLegacyRecordDO> selectRecords(@Param("query") RecordsPageQuery query);
}
