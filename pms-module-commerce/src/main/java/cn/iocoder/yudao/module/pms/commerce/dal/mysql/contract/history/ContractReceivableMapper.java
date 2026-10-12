package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history.ContractReceivableDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ContractReceivableMapper extends BaseMapperX<ContractReceivableDO> {
    ContractReceivableDO selectSourceRevisionForUpdate(@Param("query") ContractReceivableRevisionQuery query);
    List<ContractReceivableDO> selectProjectPage(@Param("query") ContractHistoryPageQuery query);
    long countProject(@Param("query") ContractHistoryPageQuery query);
}
