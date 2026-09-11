package cn.iocoder.yudao.module.pms.asset.dal.mysql.customerreference;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider.Query;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface AssetProjectCustomerReferenceMapper {
    long countReferences(@Param("query") Query query);
}
