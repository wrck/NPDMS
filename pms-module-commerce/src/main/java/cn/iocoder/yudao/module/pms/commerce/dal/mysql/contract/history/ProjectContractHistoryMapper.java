package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history.ProjectContractHistoryDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.history.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ProjectContractHistoryMapper extends BaseMapperX<ProjectContractHistoryDO> {
    ProjectContractHistoryDO selectSourceRevisionForUpdate(@Param("query") ContractHistoryRevisionQuery query);
    List<ProjectContractHistoryDO> selectProjectPage(@Param("query") ContractHistoryPageQuery query);
    long countProject(@Param("query") ContractHistoryPageQuery query);
    List<Long> selectExactCurrentContractIds(@Param("query") ContractHistoryContractQuery query);
}
