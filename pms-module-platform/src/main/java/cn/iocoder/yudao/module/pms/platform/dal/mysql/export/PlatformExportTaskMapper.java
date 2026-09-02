package cn.iocoder.yudao.module.pms.platform.dal.mysql.export;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskActorQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskIdentityQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskRetryUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskDueQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query.ExportTaskStatusUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlatformExportTaskMapper extends BaseMapperX<PlatformExportTaskDO> {

    int insertIfAbsent(PlatformExportTaskDO row);

    PlatformExportTaskDO selectByIdentity(@Param("query") ExportTaskIdentityQuery query);

    PlatformExportTaskDO selectByActor(@Param("query") ExportTaskActorQuery query);

    PlatformExportTaskDO selectByActorForUpdate(@Param("query") ExportTaskActorQuery query);

    int retryFailed(@Param("update") ExportTaskRetryUpdate update);

    java.util.List<PlatformExportTaskDO> selectRequestedForUpdate(@Param("query") ExportTaskDueQuery query);

    java.util.List<PlatformExportTaskDO> selectExpiredForUpdate(@Param("query") ExportTaskDueQuery query);

    int transition(@Param("update") ExportTaskStatusUpdate update);
}
