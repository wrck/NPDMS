package cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query;
import java.math.BigDecimal;
import lombok.Builder;
@Builder
public record ProjectProductConfigurationBusinessQuery(Long tenantId,Long projectId,String projectCode,String executionNo,Integer itemGroup,String productCode,String parentCode,BigDecimal quantity,String bomPath,String productModel,String productDescription,Integer level,String dataSource) {}
