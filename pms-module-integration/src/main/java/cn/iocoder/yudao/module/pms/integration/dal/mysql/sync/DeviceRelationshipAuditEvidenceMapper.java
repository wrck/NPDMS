package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/** INT evidence only. No asset table is queried here. */
@Mapper
public interface DeviceRelationshipAuditEvidenceMapper {
    CorrectionContext selectCorrectionContextForUpdate(@Param("query") SyncQueries.Id query);
    Receipt selectReceiptForUpdate(@Param("query") SyncQueries.Id query);
    List<SyncBindingDO> selectBindingsForUpdate(@Param("query") SyncQueries.SourceKeys query);
    record Receipt(Long id,Long tenantId,Long taskId,String status,Boolean preview,Boolean deleted,
            Integer readCount,String resultDigest,String configDigest,String summaryJson,
            LocalDateTime createTime,LocalDateTime updateTime) {}
    record CorrectionContext(Long taskId,Long tenantId,String adapter,String sourceSystem,Long connectionId,
            Integer version,String mode,Boolean resetMappingsBeforeLoad,Long activeRunId,Long runTaskId,
            Long runTenantId,Integer runConfigVersion,Boolean runPreview,String runStatus,String actorCode) {}
}
