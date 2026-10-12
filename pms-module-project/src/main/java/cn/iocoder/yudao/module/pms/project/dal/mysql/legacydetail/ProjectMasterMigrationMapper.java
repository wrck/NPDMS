package cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail;

import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi.Facts;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** Formal fields only; history initialization cannot modify runtime plans, members or snapshots. */
@Mapper
public interface ProjectMasterMigrationMapper {
    record IdentityQuery(Long tenantId, Long projectId) {}
    record CodeQuery(Long tenantId, String projectCode) {}
    record InitializationUpdate(Long tenantId, Long projectId, Long expectedVersion,
                                String expectedLifecycleStatus, String expectedCurrentStage, Facts facts) {}
    record HistoricalRoot(Facts facts,Long departmentId,String departmentCode,String departmentName) {}
    ProjectMasterDO selectIdentity(@Param("query") IdentityQuery query);
    ProjectMasterDO selectIdentityForUpdate(@Param("query") IdentityQuery query);
    List<Long> selectExactCodeIds(@Param("query") CodeQuery query);
    int initializeFormalIfMatch(@Param("query") InitializationUpdate query);
    int insertHistoricalRoot(@Param("query") HistoricalRoot query);
}
