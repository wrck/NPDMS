package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalBatchQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalPageQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArrivalAcceptanceMapper extends BaseMapperX<ArrivalAcceptanceDO> {

    ArrivalAcceptanceDO selectByBatch(@Param("query") ArrivalBatchQuery query);

    ArrivalAcceptanceDO selectRow(@Param("query") ArrivalRowQuery query);

    ArrivalAcceptanceDO selectForUpdate(@Param("query") ArrivalRowQuery query);

    List<ArrivalAcceptanceDO> selectConfirmedByProject(
            @Param("query") ArrivalProjectFactQuery query);

    List<ArrivalAcceptanceDO> selectPageRowsInternal(@Param("query") ArrivalPageQuery query);

    long selectPageCountInternal(@Param("query") ArrivalPageQuery query);

    default List<ArrivalAcceptanceDO> selectPageRows(ArrivalPageQuery query) {
        return query.visibleProjectIds().isEmpty() ? List.of() : selectPageRowsInternal(query);
    }

    default long selectPageCount(ArrivalPageQuery query) {
        return query.visibleProjectIds().isEmpty() ? 0L : selectPageCountInternal(query);
    }
}
