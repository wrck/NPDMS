package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.OrderLineFieldCarryQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DppmsOrderLineFieldCarryMapper {
    @Data @EqualsAndHashCode(callSuper = true)
    class Current extends SalesOrderLineDO {
        private String fieldsFingerprint;
        private String protectedFingerprint;
    }
    record FieldUpdate(Long tenantId, Integer expectedVersion, SalesOrderLineDO row) {}
    List<Current> selectCurrent(@Param("query") OrderLineFieldCarryQuery query);
    List<Current> selectCurrentForUpdate(@Param("query") OrderLineFieldCarryQuery query);
    int updateFields(@Param("query") FieldUpdate query);
}
