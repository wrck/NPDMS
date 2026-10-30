package cn.iocoder.yudao.module.pms.engineering.service.arrival;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;import java.util.Objects;
@Component @RequiredArgsConstructor
public class ArrivalDeliveryEvidenceProvider implements DeliveryBusinessObjectEvidenceProvider {
 private final ArrivalMapper arrivals;
 public boolean supports(String type){return "arrival".equals(type);}
 public void validateCurrent(Long tenant,Long project,String id,Long revision){var row=arrivals.selectById(Long.valueOf(id));if(row==null||!Objects.equals(tenant,row.getTenantId())||!Objects.equals(project,row.getProjectId())||revision!=null||!Integer.valueOf(1).equals(row.getStatus()))throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID","Actual signed arrival required");}
 public Identity identity(Long tenant,Long project,String id,Long revision){validateCurrent(tenant,project,id,revision);return new Identity("IMP","arrival",Long.valueOf(id),"RECEIPT","arrival",id,null);}
}
