package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationSourceRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MigrationSourceRecordMapper extends BaseMapperX<MigrationSourceRecordDO> {

    default MigrationSourceRecordDO selectByIdentity(MigrationSourceIdentityQuery query) {
        return selectOne(new LambdaQueryWrapperX<MigrationSourceRecordDO>()
                .eq(MigrationSourceRecordDO::getTenantId, query.tenantId())
                .eq(MigrationSourceRecordDO::getBatchId, query.batchId())
                .eq(MigrationSourceRecordDO::getSourceSystem, query.sourceSystem())
                .eq(MigrationSourceRecordDO::getSourceTable, query.sourceTable())
                .eq(MigrationSourceRecordDO::getSourceRecordKey, query.sourceRecordKey()));
    }

    MigrationSourceRecordDO selectByBatchAndIdForUpdate(@Param("query") MigrationSourceIdQuery query);

    List<MigrationSourceRecordDO> selectCursorPage(@Param("query") MigrationSourceCursorQuery query);

    MigrationBatchClassificationSummary selectClassificationSummary(
            @Param("query") MigrationBatchIdQuery query);
}
