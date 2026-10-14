package cn.iocoder.yudao.module.pms.engineering.dal.mysql.implementationreadiness;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.implementationreadiness.ImplementationReadinessSnapshotDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ImplementationReadinessSnapshotMapper extends BaseMapperX<ImplementationReadinessSnapshotDO> {
    ImplementationReadinessSnapshotDO latest(@Param("query") ReadinessSnapshotLookup query);
    Integer lastNumber(@Param("query") ReadinessProjectQuery query);
}
