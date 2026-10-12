package cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceSourceRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface DeviceSourceRelationshipMapper extends BaseMapperX<DeviceSourceRelationshipDO> {
    DeviceSourceRelationshipDO selectSourceForUpdate(@Param("query") DeviceSourceRelationshipIdentityQuery query);
    List<DeviceSourceRelationshipDO> selectRelationshipPage(@Param("query") DeviceSourceRelationshipPageQuery query);
    long countRelationships(@Param("query") DeviceSourceRelationshipPageQuery query);
    int insertHistorical(@Param("command") HistoricalInsert command);
    int correctHistoricalAudit(@Param("command") HistoricalAuditWrite command);
    record HistoricalAuditWrite(Long tenantId,Long id,Integer expectedVersion,
            java.time.LocalDateTime createTime,java.time.LocalDateTime updateTime) {}
    /** Wrapper prevents framework entity auto-fill from replacing original historical dates. */
    record HistoricalInsert(DeviceSourceRelationshipDO row) {}
}
