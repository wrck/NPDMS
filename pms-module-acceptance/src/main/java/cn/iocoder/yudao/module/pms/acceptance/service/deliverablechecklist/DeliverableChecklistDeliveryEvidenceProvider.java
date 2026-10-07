package cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;import java.util.Objects;
@Component @RequiredArgsConstructor
public class DeliverableChecklistDeliveryEvidenceProvider implements DeliveryBusinessObjectEvidenceProvider {
 private final DeliverableChecklistMapper checklists;
 public boolean supports(String type){return "deliverableChecklist".equals(type);}
 public void validateCurrent(Long tenant,Long project,String id,Long revision){var row=checklists.selectById(Long.valueOf(id));if(row==null||!Objects.equals(tenant,row.getTenantId())||!Objects.equals(project,row.getProjectId())||revision!=null||!Integer.valueOf(2).equals(row.getStatus()))throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID","Actual passed checklist required");}
 public Identity identity(Long tenant,Long project,String id,Long revision){validateCurrent(tenant,project,id,revision);return new Identity("ACC","deliverableChecklist",Long.valueOf(id),"DELIVERABLE_CHECKLIST","deliverableChecklist",id,null);}
}
