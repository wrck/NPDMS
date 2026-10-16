package cn.iocoder.yudao.module.pms.engineering.service.materialexchange.business;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.business.MaterialExchangeBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.business.MaterialExchangeBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.materialexchange.MaterialExchangeService;
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
public class MaterialExchangeBusinessService extends DefaultProjectBusinessService<MaterialExchangeBusinessMapper,MaterialExchangeBusinessDO> {
    private final MaterialExchangeMapper nativeMapper;
    private final MaterialExchangeService nativeService;
    private final NativeAttachmentRegistration attachments;
    private final MaterialExchangeBusinessDetails details;

    @Override protected void beforeCreate(MaterialExchangeBusinessDO row) {
        row.setStatus(0);attachments.requireLegacyUnchanged(null,row.getReasonFiles());
        row.setApplicantUserId(actor().userId());row.setApplyTime(LocalDateTime.now());row.setCrmPushStatus("PENDING");row.setExchangeType("INCOMPATIBLE");
    }
    @Override protected void beforeUpdate(MaterialExchangeBusinessDO before,MaterialExchangeBusinessDO row) {
        editable(before);
        if(!Objects.equals(before.getProjectId(),row.getProjectId()) || !Objects.equals(before.getCode(),row.getCode()))throw new BusinessContractException("IDENTITY_IMMUTABLE","所属项目和申请单号不可修改");
        attachments.requireLegacyUnchanged(before.getReasonFiles(),row.getReasonFiles());
    }
    @Override protected void validateBusiness(MaterialExchangeBusinessDO row) {
        if(!defaults.validator().validate(BeanUtils.toBean(row,MaterialExchangeSaveReqVO.class)).isEmpty())throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID","请填写完整的申请内容");
        var duplicate=nativeMapper.selectByCode(row.getCode());
        if(duplicate!=null && !Objects.equals(duplicate.getId(),row.getId()))throw new BusinessContractException("CODE_DUPLICATE","申请单号已存在");
        if(row.getQuantity()==null)throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID","请先选择设备或物料明细");
    }
    @Override protected void afterUpdate(MaterialExchangeBusinessDO before,MaterialExchangeBusinessDO row){attachments.register(NativeAttachmentKind.MATERIAL_EXCHANGE,row.getId());}
    @Override protected void beforeDelete(MaterialExchangeBusinessDO row){editable(row);}
    @Override protected void afterDelete(MaterialExchangeBusinessDO row){details.delete(row);}
    @Override protected void validateDelivery(MaterialExchangeBusinessDO row,boolean write){if(write)editable(row);}
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("APPLICATION_APPROVED","申请已通过");}
    @Override protected Map<String,Boolean> runtimeFacts(MaterialExchangeBusinessDO row){return Map.of("APPLICATION_APPROVED",Objects.equals(row.getStatus(),3));}
    @Override protected boolean runtimeHandlingCompleted(MaterialExchangeBusinessDO row){return Objects.equals(row.getStatus(),3);}
    @Override protected LocalDateTime runtimeFormedAt(MaterialExchangeBusinessDO row){return row.getApproveTime();}
    private void editable(MaterialExchangeBusinessDO row){if(row.getStatus()==null || !Set.of(0,4).contains(row.getStatus()))throw new BusinessContractException("STATE_NOT_EDITABLE","只有草稿或已驳回的申请可以编辑");}
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix){return List.of(operation("submit","提交申请",prefix),operation("approve","办理申请",prefix),operation("withdraw","撤回申请",prefix),operation("terminate","终止申请",prefix));}
    private BusinessOperationDescriptor operation(String code,String name,String prefix){return new BusinessOperationDescriptor(code,1,name,BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update");}
    public BusinessOperationReceipt submit(Long id,Long version,String key){return businessAction("submit",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.submitMaterialExchange(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt withdraw(Long id,Long version,String key){return businessAction("withdraw",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.withdrawMaterialExchange(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt terminate(Long id,Long version,String key){return businessAction("terminate",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.terminateMaterialExchange(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt approve(Long id,Long version,String key,String action,String opinion){
        if(action==null || !Set.of("PASS","REJECT","RETURN","TRANSFER","COUNTERSIGN").contains(action))throw new BusinessContractException("ACTION_INVALID","请选择有效的办理动作");
        var values=new LinkedHashMap<String,Object>();values.put("action",action);values.put("opinion",opinion);
        return businessAction("approve",id,version,key,values,ReceiptOutcome.SAVED,row->{var command=new MaterialExchangeApproveReqVO();command.setId(id);command.setApproveAction(action);command.setApproveOpinion(opinion);command.setApproverUserId(actor().userId());nativeService.approveMaterialExchange(command);return mapper.selectById(id);});
    }
    @Override protected void afterRead(MaterialExchangeBusinessDO row){details.load(row);}
    @Override protected void beforeFormWrite(MaterialExchangeBusinessDO before,MaterialExchangeBusinessDO row,Map<String,Object> business){details.prepare(before,row,business,defaults.validator());}
    @Override protected void afterBodyPersisted(String operation,MaterialExchangeBusinessDO before,MaterialExchangeBusinessDO row){details.save(row);}
}
