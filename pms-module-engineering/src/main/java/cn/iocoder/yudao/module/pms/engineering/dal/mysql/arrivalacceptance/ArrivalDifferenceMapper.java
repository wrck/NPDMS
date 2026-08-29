package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalDifferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildrenQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArrivalDifferenceMapper extends BaseMapperX<ArrivalDifferenceDO> {

    List<ArrivalDifferenceDO> selectCurrentList(@Param("query") ArrivalChildrenQuery query);

    List<ArrivalDifferenceDO> selectCurrentListForUpdate(@Param("query") ArrivalChildrenQuery query);

    List<ArrivalDifferenceDO> selectEffectiveExemptionsByProject(
            @Param("query") ArrivalProjectFactQuery query);
}
