package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalTaskFactQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalTaskPageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** Only public record identity and completion state; customer token/signature data are not task facts. */
@Mapper
public interface ArrivalTaskFactMapper extends BaseMapperX<ArrivalDO> {
    default ArrivalDO selectTaskFact(ArrivalTaskFactQuery query) {
        return selectOne(new LambdaQueryWrapperX<ArrivalDO>()
                .select(ArrivalDO::getId, ArrivalDO::getTenantId, ArrivalDO::getProjectId,
                        ArrivalDO::getCode, ArrivalDO::getStatus, ArrivalDO::getVersion, ArrivalDO::getDeleted)
                .eq(ArrivalDO::getTenantId, query.tenantId()).eq(ArrivalDO::getProjectId, query.projectId())
                .eq(ArrivalDO::getId, query.arrivalId()));
    }
    ArrivalDO selectTaskFactForUpdate(@Param("query") ArrivalTaskFactQuery query);
    default List<ArrivalDO> selectAssociationPage(ArrivalTaskPageQuery query) {
        return selectPage(new Page<ArrivalDO>(1, query.pageSize(), false), new LambdaQueryWrapperX<ArrivalDO>()
                .select(ArrivalDO::getId, ArrivalDO::getTenantId, ArrivalDO::getProjectId,
                        ArrivalDO::getCode, ArrivalDO::getStatus, ArrivalDO::getVersion, ArrivalDO::getDeleted)
                .eq(ArrivalDO::getTenantId, query.tenantId()).eq(ArrivalDO::getProjectId, query.projectId())
                .gt(query.afterId() != null, ArrivalDO::getId, query.afterId()).orderByAsc(ArrivalDO::getId)).getRecords();
    }
}
