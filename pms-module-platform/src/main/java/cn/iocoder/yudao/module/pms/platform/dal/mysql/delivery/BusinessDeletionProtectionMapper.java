package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.BusinessDeletionProtectionQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface BusinessDeletionProtectionMapper {
    boolean hasProtectedReferences(@Param("query") BusinessDeletionProtectionQuery query);
}
