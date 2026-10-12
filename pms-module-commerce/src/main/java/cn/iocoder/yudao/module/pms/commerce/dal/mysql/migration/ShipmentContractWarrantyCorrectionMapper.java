package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.query.ShipmentContractWarrantyCorrectionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface ShipmentContractWarrantyCorrectionMapper {
    record Current(Long id, String warrantyYears, Integer deleted, String fingerprint, String normalizedFingerprint) {}
    Current selectCurrent(@Param("query") ShipmentContractWarrantyCorrectionQuery query);
    Current selectCurrentForUpdate(@Param("query") ShipmentContractWarrantyCorrectionQuery query);
    int normalizeEmptyYear(@Param("query") ShipmentContractWarrantyCorrectionQuery query);
}
