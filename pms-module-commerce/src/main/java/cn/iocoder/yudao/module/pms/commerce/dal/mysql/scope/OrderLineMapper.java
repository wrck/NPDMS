package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.OrderLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderLineMapper extends BaseMapperX<OrderLineDO> {
    default List<OrderLineDO> selectConfirmed() {
        return selectList(new LambdaQueryWrapperX<OrderLineDO>()
                .eq(OrderLineDO::getQuantityStatus, "CONFIRMED")
                .orderByAsc(OrderLineDO::getId));
    }

    default OrderLineDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(OrderLineDO::getId, id);
    }
}
