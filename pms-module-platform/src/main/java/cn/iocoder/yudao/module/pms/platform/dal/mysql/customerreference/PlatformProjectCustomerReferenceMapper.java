package cn.iocoder.yudao.module.pms.platform.dal.mysql.customerreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface PlatformProjectCustomerReferenceMapper {
    record Query(Long tenantId, String projectId) { }
    long countReferences(@Param("query") Query query);
}
