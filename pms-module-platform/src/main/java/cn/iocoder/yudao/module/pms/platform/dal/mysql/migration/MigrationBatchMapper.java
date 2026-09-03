package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationBatchDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MigrationBatchMapper extends BaseMapperX<MigrationBatchDO> {

    default MigrationBatchDO selectByIdentity(MigrationBatchIdentityQuery query) {
        return selectOne(new LambdaQueryWrapperX<MigrationBatchDO>()
                .eq(MigrationBatchDO::getTenantId, query.tenantId())
                .eq(MigrationBatchDO::getOwnerContextCode, query.ownerContextCode())
                .eq(MigrationBatchDO::getPurposeCode, query.purposeCode())
                .eq(MigrationBatchDO::getReleaseId, query.releaseId())
                .eq(MigrationBatchDO::getSourceSystem, query.sourceSystem())
                .eq(MigrationBatchDO::getSourceTable, query.sourceTable()));
    }

    MigrationBatchDO selectByTenantAndIdForUpdate(@Param("query") MigrationBatchIdQuery query);

    MigrationBatchDO selectNextStagedForUpdate(@Param("query") MigrationBatchClaimQuery query);

    int transition(@Param("update") MigrationBatchTransitionUpdate update);

    int claim(@Param("query") MigrationBatchIdQuery query, @Param("expectedVersion") int expectedVersion);

    int complete(@Param("update") MigrationBatchCompletionUpdate update);
}
