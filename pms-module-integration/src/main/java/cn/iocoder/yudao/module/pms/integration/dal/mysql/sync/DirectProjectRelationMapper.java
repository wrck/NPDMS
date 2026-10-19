package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi.Relation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DirectProjectRelationMapper {
    record IdentityQuery(Long tenantId, Long projectId, Long contractId) {}
    record HistoricalWrite(Relation before, Relation after) {}
    Relation selectIdentity(@Param("query") IdentityQuery query);
    Relation selectIdentityForUpdate(@Param("query") IdentityQuery query);
    int insertHistorical(@Param("row") Relation row);
    int correctHistorical(@Param("write") HistoricalWrite write);
    int restoreWithdrawn(@Param("write") HistoricalWrite write);
}
