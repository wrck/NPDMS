package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalLineDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalChildrenQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query.ArrivalProjectFactQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArrivalLineMapper extends BaseMapperX<ArrivalLineDO> {

    List<ArrivalLineDO> selectCurrentList(@Param("query") ArrivalChildrenQuery query);

    List<ArrivalLineDO> selectCurrentListForUpdate(@Param("query") ArrivalChildrenQuery query);

    List<ArrivalLineDO> selectConfirmedAcceptedByProject(
            @Param("query") ArrivalProjectFactQuery query);
}
