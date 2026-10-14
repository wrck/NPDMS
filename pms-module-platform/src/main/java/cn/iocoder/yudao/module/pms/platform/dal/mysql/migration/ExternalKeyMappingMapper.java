package cn.iocoder.yudao.module.pms.platform.dal.mysql.migration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.ExternalKeyMappingDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationMappingSourceQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourceOnlyQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationSourcePageQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ExternalKeyMappingMapper extends BaseMapperX<ExternalKeyMappingDO> {
    int insertRows(@org.apache.ibatis.annotations.Param("rows") List<ExternalKeyMappingDO> rows);

    /** Bound the pre-insert existence check to this batch even while table statistics lag bulk loading. */
    List<ExternalKeyMappingDO> selectSourcePage(
            @org.apache.ibatis.annotations.Param("query") MigrationSourcePageQuery query);

    default List<ExternalKeyMappingDO> selectListBySourceRecordIds(MigrationMappingSourceQuery query) {
        if (query.sourceRecordIds() == null || query.sourceRecordIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<ExternalKeyMappingDO>()
                .eq(ExternalKeyMappingDO::getTenantId, query.tenantId())
                .in(ExternalKeyMappingDO::getSourceRecordId, query.sourceRecordIds())
                .orderByAsc(ExternalKeyMappingDO::getId));
    }

    default List<ExternalKeyMappingDO> selectListBySource(MigrationSourceOnlyQuery query) {
        return selectList(new LambdaQueryWrapperX<ExternalKeyMappingDO>()
                .eq(ExternalKeyMappingDO::getTenantId, query.tenantId())
                .eq(ExternalKeyMappingDO::getSourceRecordId, query.sourceRecordId())
                .orderByAsc(ExternalKeyMappingDO::getId));
    }
}
