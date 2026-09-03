package cn.iocoder.yudao.module.pms.platform.dal.mysql.export;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportAuditDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformExportAuditMapper extends BaseMapperX<PlatformExportAuditDO> {

    Integer selectNextSequenceForUpdate(@Param("tenantId") Long tenantId,
                                        @Param("taskId") Long taskId);
}
