package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Mapper
public interface DppmsProductScopeMapper {
    record OrderLineQuery(Long tenantId,Long projectId,String contractNo,String orderNo,String lineNo,String productCode) {}
    record IdentityQuery(Long tenantId,String evidence) {}
    record PageQuery(Long tenantId,Long projectId,int offset,int limit) {}
    record ProductDetailQuery(Long tenantId,Long deliveryScopeId) {}
    @Getter @Setter
    class ProductProjection extends DeliveryScopeDO {
        private String contractNo;
        private String unitCode;
        private BigDecimal orderQuantity;
    }
    List<SalesOrderLineDO> selectAssociatedLines(@Param("query") OrderLineQuery query);
    List<SalesOrderLineDO> lockAssociatedLines(@Param("query") OrderLineQuery query);
    List<DeliveryScopeDO> selectIdentity(@Param("query") IdentityQuery query);
    List<DeliveryScopeDO> lockIdentity(@Param("query") IdentityQuery query);
    int insertHistoricalScope(@Param("row") DeliveryScopeDO row);
    List<DeliveryScopeDetailDO> selectProductDetails(@Param("query") ProductDetailQuery query);
    List<DeliveryScopeDetailDO> selectProductDetailsForUpdate(@Param("query") ProductDetailQuery query);
    int insertHistoricalProductDetail(@Param("row") DeliveryScopeDetailDO row);
    List<ProductProjection> selectProjectPage(@Param("query") PageQuery query);
    long countProject(@Param("query") PageQuery query);
}
