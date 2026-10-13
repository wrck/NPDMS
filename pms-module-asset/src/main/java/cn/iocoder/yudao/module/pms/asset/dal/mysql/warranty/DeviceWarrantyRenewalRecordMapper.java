package cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRenewalRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Set;

@Mapper
public interface DeviceWarrantyRenewalRecordMapper {
    record Identity(Long tenantId, Long id) {}
    record Write(DeviceWarrantyRenewalRecordDO row, Integer expectedVersion) {}
    /** A device identity or authorized project contracts is mandatory; empty scope is never unrestricted. */
    record PageQuery(Long tenantId, String deviceSn, Set<String> contractNumbers, String sn,
                     long offset, int limit) {}
    DeviceWarrantyRenewalRecordDO selectForUpdate(@Param("query") Identity query);
    int insertRecord(@Param("row") DeviceWarrantyRenewalRecordDO row);
    int updateRecord(@Param("command") Write command);
    long count(@Param("query") PageQuery query);
    List<DeviceWarrantyRenewalRecordDO> selectPage(@Param("query") PageQuery query);
}
