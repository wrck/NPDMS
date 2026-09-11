package cn.iocoder.yudao.module.pms.service.dal.mysql.customerreference;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider.Query;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface ServiceProjectCustomerReferenceMapper {
    long countReferences(@Param("query") Query query);
}
