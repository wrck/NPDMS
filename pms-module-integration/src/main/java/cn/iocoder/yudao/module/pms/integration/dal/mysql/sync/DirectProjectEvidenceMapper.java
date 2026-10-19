package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface DirectProjectEvidenceMapper {
 record SourceIdentityQuery(Long tenantId,String sourceProjectKey) {}
 record RecordsPageQuery(Long tenantId,Long snapshotId,String domainCode,String sourceKey,String parentDomain,String parentSourceKey,int offset,int limit) {}
 DirectProjectSourceRow selectSource(@Param("query") SourceIdentityQuery query);
 DirectProjectSourceRow selectSourceForUpdate(@Param("query") SourceIdentityQuery query);
 List<DirectProjectRecordRow> selectRecords(@Param("query") RecordsPageQuery query);
}
