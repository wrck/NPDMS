package cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query;
import java.math.BigDecimal;
import lombok.Builder;
@Builder
public record ProjectLeaseProductBusinessQuery(Long tenantId,Long projectId,String projectCode,String executionNo,String productCategory,String productType,String productCode,String productModel,String productName,BigDecimal quantity,String memo,BigDecimal leaseDuration,String dataSource) {}
