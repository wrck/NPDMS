package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalDifferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.projection.ArrivalProjectFactAllocation;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildrenQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildrenBatchQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildRevisionMutation;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactAllocationQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArrivalDifferenceMapper extends BaseMapperX<ArrivalDifferenceDO> {

    List<ArrivalDifferenceDO> selectCurrentList(@Param("query") ArrivalChildrenQuery query);

    List<ArrivalDifferenceDO> selectCurrentListByAcceptanceIdsInternal(
            @Param("query") ArrivalChildrenBatchQuery query);

    default List<ArrivalDifferenceDO> selectCurrentListByAcceptanceIds(ArrivalChildrenBatchQuery query) {
        return query.arrivalAcceptanceIds().isEmpty() ? List.of() : selectCurrentListByAcceptanceIdsInternal(query);
    }

    List<ArrivalDifferenceDO> selectCurrentListForUpdate(@Param("query") ArrivalChildrenQuery query);

    int clearCurrentIfMatch(@Param("query") ArrivalChildRevisionMutation update);

    List<ArrivalDifferenceDO> selectEffectiveExemptionsByProject(
            @Param("query") ArrivalProjectFactQuery query);

    List<ArrivalDifferenceDO> selectEffectiveExemptionsByProjectForUpdate(
            @Param("query") ArrivalProjectFactQuery query);

    List<ArrivalProjectFactAllocation> selectLatestAllocatedDifferencesForUpdate(
            @Param("query") ArrivalProjectFactAllocationQuery query);
}
