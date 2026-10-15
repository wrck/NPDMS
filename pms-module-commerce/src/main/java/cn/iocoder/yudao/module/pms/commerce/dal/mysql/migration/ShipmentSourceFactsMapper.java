package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ShipmentSourceFactsMapper {
    List<ShipmentSourceFactsApi.Fact> selectFacts(@Param("query") Query query);
    record Query(Long tenantId,List<String> packageSourceKeys) {}
}
