package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsLocalContractSourceApi.Contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DppmsLocalContractSourceMapper {
    record Page(Long tenantId, List<String> contractNumbers, String afterCompany, String afterContract) {}
    List<Contract> selectPage(@Param("query") Page query);
}
