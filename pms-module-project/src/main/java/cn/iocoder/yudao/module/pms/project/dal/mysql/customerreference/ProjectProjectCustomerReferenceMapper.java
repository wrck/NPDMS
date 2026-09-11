package cn.iocoder.yudao.module.pms.project.dal.mysql.customerreference;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider.Query;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface ProjectProjectCustomerReferenceMapper {
    long countReferences(@Param("query") Query query);
}
