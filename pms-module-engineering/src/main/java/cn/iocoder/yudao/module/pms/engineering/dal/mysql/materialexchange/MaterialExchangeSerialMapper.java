package cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeSerialDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.query.MaterialExchangeSerialQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface MaterialExchangeSerialMapper extends BaseMapperX<MaterialExchangeSerialDO> {
    default List<MaterialExchangeSerialDO> selectByExchange(MaterialExchangeSerialQuery query) {
        return selectList(new LambdaQueryWrapperX<MaterialExchangeSerialDO>()
                .eq(MaterialExchangeSerialDO::getExchangeId, query.exchangeId())
                .orderByAsc(MaterialExchangeSerialDO::getId));
    }

    default void deleteByExchange(MaterialExchangeSerialQuery query) {
        delete(new LambdaQueryWrapperX<MaterialExchangeSerialDO>()
                .eq(MaterialExchangeSerialDO::getExchangeId, query.exchangeId()));
    }
}
