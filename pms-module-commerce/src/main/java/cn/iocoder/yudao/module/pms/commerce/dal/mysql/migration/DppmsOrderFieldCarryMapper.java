package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.OrderFieldCarryQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DppmsOrderFieldCarryMapper {
    @Data @EqualsAndHashCode(callSuper = true)
    class Current extends SalesOrderDO {
        private String fieldsFingerprint;
        private String protectedFingerprint;
    }
    record FieldUpdate(Long tenantId, Long expectedVersion, SalesOrderDO row) {}
    List<Current> selectCurrent(@Param("query") OrderFieldCarryQuery query);
    List<Current> selectCurrentForUpdate(@Param("query") OrderFieldCarryQuery query);
    int updateFields(@Param("query") FieldUpdate query);
}
