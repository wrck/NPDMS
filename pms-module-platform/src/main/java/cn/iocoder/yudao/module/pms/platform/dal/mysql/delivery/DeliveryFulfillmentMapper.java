package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryFulfillmentDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryRequirementMaterialQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryFulfillmentIdentityQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DeliveryFulfillmentMapper extends BaseMapperX<DeliveryFulfillmentDO> {
    DeliveryFulfillmentDO selectIdentity(@Param("query") DeliveryFulfillmentIdentityQuery query);
    List<DeliveryFulfillmentDO> selectListForRequirement(@Param("query") DeliveryRequirementMaterialQuery query);
}
