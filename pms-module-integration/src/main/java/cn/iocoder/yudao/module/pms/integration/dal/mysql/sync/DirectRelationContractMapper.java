package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface DirectRelationContractMapper {
 record ContractMigrationIdentityQuery(Long tenantId,String companyCode,String contractNo) {}
 record ContractIdLockQuery(Long tenantId,Long contractId) {}
 List<DirectRelationContractRow> selectMigrationIdentity(@Param("query") ContractMigrationIdentityQuery query);
 DirectRelationContractRow selectByIdForUpdate(@Param("query") ContractIdLockQuery query);
}
