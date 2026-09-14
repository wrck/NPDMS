package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.ExternalKeyMappingDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourceOnlyQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourcePageQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ExternalKeyMappingMapper extends BaseMapperX<ExternalKeyMappingDO> {

    default List<ExternalKeyMappingDO> selectSourcePage(MigrationSourcePageQuery query) {
        if (query.sourceRecordIds() == null || query.sourceRecordIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ExternalKeyMappingDO>()
                .eq(ExternalKeyMappingDO::getTenantId, query.tenantId())
                .eq(ExternalKeyMappingDO::getBatchId, query.batchId())
                .in(ExternalKeyMappingDO::getSourceRecordId, query.sourceRecordIds()));
    }

    default List<ExternalKeyMappingDO> selectListBySource(MigrationSourceOnlyQuery query) {
        return selectList(new LambdaQueryWrapperX<ExternalKeyMappingDO>()
                .eq(ExternalKeyMappingDO::getTenantId, query.tenantId())
                .eq(ExternalKeyMappingDO::getSourceRecordId, query.sourceRecordId())
                .orderByAsc(ExternalKeyMappingDO::getId));
    }
}
