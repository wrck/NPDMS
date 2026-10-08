package cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import org.springframework.stereotype.Service;
import java.util.Map;
@Service @BusinessEntityService(ownerModule="ACC", entityType="deliverableChecklist")
public class DeliverableChecklistBusinessService extends ExtensibleBusinessApplicationService {
    private final DeliverableChecklistMapper mapper;
    private final AcceptanceRecordCodeGenerator codes;
    private final DeliverableChecklistService lifecycle;
    public DeliverableChecklistBusinessService(DefaultBusinessApplicationService defaults, DeliverableChecklistMapper mapper,
            AcceptanceRecordCodeGenerator codes, DeliverableChecklistService lifecycle) {
        super(defaults); this.mapper=mapper; this.codes=codes; this.lifecycle=lifecycle;
    }
    @Override protected void initializeEntity(BusinessOperationRequest request, BaseBusinessEntity entity) {
        var checklist=(DeliverableChecklistDO) entity;
        checklist.setCode(codes.next(checklist.getProjectId(), AcceptanceRecordCodeGenerator.DELIVERABLE_CHECKLIST, mapper));
        checklist.setStatus(0);
        if(checklist.getDeliverableType()==null) checklist.setDeliverableType("REQUIRED");
    }
    @Override protected void validateBusinessOperation(BusinessOperationRequest request, Map<String,Object> current) {
        if(request.targetRef()==null) return;
        var state=current.get("status");
        if("save".equals(request.operationCode()) && !Integer.valueOf(0).equals(state)
                || "delete".equals(request.operationCode()) && !Integer.valueOf(0).equals(state) && !Integer.valueOf(3).equals(state))
            throw new BusinessContractException("CHECKLIST_STATE_INVALID", "当前状态不允许修改或删除");
    }
    @Override protected Map<String,Object> customOperationChanges(BusinessOperationRequest request, Map<String,Object> current) { return Map.of(); }
    @Override protected boolean performCustomOperation(BusinessOperationRequest request, Map<String,Object> current) {
        Long id=request.targetRef().entity().entityId();
        switch(request.operationCode()) {
            case "submit" -> lifecycle.submitDeliverableChecklist(id);
            case "pass" -> lifecycle.passDeliverableChecklist(id);
            case "reject" -> lifecycle.rejectDeliverableChecklist(id);
            default -> throw new BusinessContractException("OPERATION_NOT_DECLARED", "清单未开放该命令");
        }
        return true;
    }
}
