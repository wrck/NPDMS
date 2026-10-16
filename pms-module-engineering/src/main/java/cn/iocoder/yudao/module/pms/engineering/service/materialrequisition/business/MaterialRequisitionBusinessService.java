package cn.iocoder.yudao.module.pms.engineering.service.materialrequisition.business;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition.business.MaterialRequisitionBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialrequisition.MaterialRequisitionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialrequisition.business.MaterialRequisitionBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.materialrequisition.MaterialRequisitionService;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.*;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/** Ordinary persistence is inherited. Native domain commands retain the existing state machine. */
@Service @RequiredArgsConstructor
public class MaterialRequisitionBusinessService extends DefaultProjectBusinessService<MaterialRequisitionBusinessMapper,MaterialRequisitionBusinessDO> {
    private final MaterialRequisitionMapper nativeMapper;
    private final MaterialRequisitionService nativeService;
    private final NativeAttachmentRegistration attachments;
    private final cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi devices;

    @Override protected void beforeCreate(MaterialRequisitionBusinessDO row) {
        row.setStatus(0);attachments.requireLegacyUnchanged(null,row.getAttachmentFiles());
    }
    @Override protected void beforeUpdate(MaterialRequisitionBusinessDO before,MaterialRequisitionBusinessDO row) {
        editable(before);
        if(!Objects.equals(before.getProjectId(),row.getProjectId()) || !Objects.equals(before.getCode(),row.getCode()))throw new BusinessContractException("IDENTITY_IMMUTABLE","所属项目和申请单号不可修改");
        attachments.requireLegacyUnchanged(before.getAttachmentFiles(),row.getAttachmentFiles());
    }
    @Override protected void validateBusiness(MaterialRequisitionBusinessDO row) {
        if(!defaults.validator().validate(BeanUtils.toBean(row,MaterialRequisitionSaveReqVO.class)).isEmpty())throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID","请填写完整的申请内容");
        var duplicate=nativeMapper.selectByCode(row.getCode());
        if(duplicate!=null && !Objects.equals(duplicate.getId(),row.getId()))throw new BusinessContractException("CODE_DUPLICATE","申请单号已存在");
        if(row.getEquipmentId()!=null)devices.validateSelection(row.getProjectId(),List.of(row.getEquipmentId()));
    }
    @Override protected void afterUpdate(MaterialRequisitionBusinessDO before,MaterialRequisitionBusinessDO row){attachments.register(NativeAttachmentKind.MATERIAL_REQUISITION,row.getId());}
    @Override protected void beforeDelete(MaterialRequisitionBusinessDO row){editable(row);}
    @Override protected void validateDelivery(MaterialRequisitionBusinessDO row,boolean write){if(write)editable(row);}
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("APPLICATION_APPROVED","申请已通过");}
    @Override protected Map<String,Boolean> runtimeFacts(MaterialRequisitionBusinessDO row){return Map.of("APPLICATION_APPROVED",Objects.equals(row.getStatus(),3));}
    @Override protected boolean runtimeHandlingCompleted(MaterialRequisitionBusinessDO row){return Objects.equals(row.getStatus(),3);}
    @Override protected LocalDateTime runtimeFormedAt(MaterialRequisitionBusinessDO row){return row.getApproveTime();}
    private void editable(MaterialRequisitionBusinessDO row){if(row.getStatus()==null || !Set.of(0,4).contains(row.getStatus()))throw new BusinessContractException("STATE_NOT_EDITABLE","只有草稿或已驳回的申请可以编辑");}
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix){return List.of(operation("submit","提交申请",prefix),operation("approve","办理申请",prefix),operation("withdraw","撤回申请",prefix),operation("terminate","终止申请",prefix));}
    private BusinessOperationDescriptor operation(String code,String name,String prefix){return new BusinessOperationDescriptor(code,1,name,BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update");}
    public BusinessOperationReceipt submit(Long id,Long version,String key){return businessAction("submit",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.submitMaterialRequisition(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt withdraw(Long id,Long version,String key){return businessAction("withdraw",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.withdrawMaterialRequisition(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt terminate(Long id,Long version,String key){return businessAction("terminate",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.terminateMaterialRequisition(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt approve(Long id,Long version,String key,String action,String opinion){
        if(action==null || !Set.of("PASS","REJECT","RETURN","TRANSFER","COUNTERSIGN").contains(action))throw new BusinessContractException("ACTION_INVALID","请选择有效的办理动作");
        var values=new LinkedHashMap<String,Object>();values.put("action",action);values.put("opinion",opinion);
        return businessAction("approve",id,version,key,values,ReceiptOutcome.SAVED,row->{var command=new MaterialRequisitionApproveReqVO();command.setId(id);command.setApproveAction(action);command.setApproveOpinion(opinion);command.setApproverUserId(actor().userId());nativeService.approveMaterialRequisition(command);return mapper.selectById(id);});
    }
}
