package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import org.apache.ibatis.annotations.*;
@Mapper public interface DirectScopeLookupMapper {
 record Identity(Long tenantId,Long id) {}
 record ProjectVersion(Long tenantId,Long projectId) {}
 DirectDeliveryScopeRow selectById(@Param("query") Identity query);
 DirectScopeVersionRow current(@Param("query") ProjectVersion query);
 DirectScopeVersionRow lockExisting(@Param("query") ProjectVersion query);
}
