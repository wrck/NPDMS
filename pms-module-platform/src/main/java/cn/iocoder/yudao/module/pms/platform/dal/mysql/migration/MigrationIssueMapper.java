package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationIssueDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationIssueCloseUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationIssueIdQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourceOnlyQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourcePageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MigrationIssueMapper extends BaseMapperX<MigrationIssueDO> {

    default List<MigrationIssueDO> selectSourcePage(MigrationSourcePageQuery query) {
        if (query.sourceRecordIds() == null || query.sourceRecordIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<MigrationIssueDO>()
                .eq(MigrationIssueDO::getTenantId, query.tenantId())
                .eq(MigrationIssueDO::getBatchId, query.batchId())
                .in(MigrationIssueDO::getSourceRecordId, query.sourceRecordIds()));
    }

    default List<MigrationIssueDO> selectListBySource(MigrationSourceOnlyQuery query) {
        return selectList(new LambdaQueryWrapperX<MigrationIssueDO>()
                .eq(MigrationIssueDO::getTenantId, query.tenantId())
                .eq(MigrationIssueDO::getSourceRecordId, query.sourceRecordId())
                .orderByAsc(MigrationIssueDO::getId));
    }

    MigrationIssueDO selectByTenantAndIdForUpdate(@Param("query") MigrationIssueIdQuery query);

    int close(@Param("update") MigrationIssueCloseUpdate update);
}
