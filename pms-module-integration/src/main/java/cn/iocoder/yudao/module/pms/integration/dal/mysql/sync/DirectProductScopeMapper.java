package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import org.apache.ibatis.annotations.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Mapper
public interface DirectProductScopeMapper {
    record OrderLineQuery(Long tenantId,Long projectId,String contractNo,String orderNo,String lineNo,String productCode) {}
    record IdentityQuery(Long tenantId,String evidence) {}
    record ProductDetailQuery(Long tenantId,Long deliveryScopeId) {}
    List<DirectScopeOrderLineRow> selectAssociatedLines(@Param("query") OrderLineQuery query);
    List<DirectScopeOrderLineRow> lockAssociatedLines(@Param("query") OrderLineQuery query);
    List<DirectDeliveryScopeRow> selectIdentity(@Param("query") IdentityQuery query);
    List<DirectDeliveryScopeRow> lockIdentity(@Param("query") IdentityQuery query);
    int insertHistoricalScope(@Param("row") DirectDeliveryScopeRow row);
    List<DirectScopeDetailRow> selectProductDetails(@Param("query") ProductDetailQuery query);
    List<DirectScopeDetailRow> selectProductDetailsForUpdate(@Param("query") ProductDetailQuery query);
    int insertHistoricalProductDetail(@Param("row") DirectScopeDetailRow row);
}
