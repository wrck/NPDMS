package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeDetailIdsQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeliveryScopeDetailMapper extends BaseMapperX<DeliveryScopeDetailDO> {
    List<DeliveryScopeDetailDO> selectByScopeIds(@Param("query") DeliveryScopeDetailIdsQuery query);
}
