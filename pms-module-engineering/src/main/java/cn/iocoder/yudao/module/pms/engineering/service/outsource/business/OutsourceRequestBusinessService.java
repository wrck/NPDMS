package cn.iocoder.yudao.module.pms.engineering.service.outsource.business;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.business.OutsourceRequestBusinessDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource.OutsourceRequestMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.outsource.business.OutsourceRequestBusinessMapper;
import cn.iocoder.yudao.module.pms.engineering.service.outsource.OutsourceRequestService;
import cn.iocoder.yudao.module.pms.engineering.service.attachment.*;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

/** Ordinary persistence is inherited. Native domain commands retain the existing state machine. */
@Service @RequiredArgsConstructor
public class OutsourceRequestBusinessService extends DefaultProjectBusinessService<OutsourceRequestBusinessMapper,OutsourceRequestBusinessDO> {
    private final OutsourceRequestMapper nativeMapper;
    private final OutsourceRequestService nativeService;
    private final NativeAttachmentRegistration attachments;
    private final cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.business.SiteSurveyBusinessService surveys;
    private final ThreadLocal<ProjectBusinessExecutionSelection> deletionExecution=new ThreadLocal<>();

    @Override protected void beforeCreate(OutsourceRequestBusinessDO row) {
        row.setStatus(0);attachments.requireLegacyUnchanged(null,row.getAttachmentFiles());
    }
    @Override protected void beforeUpdate(OutsourceRequestBusinessDO before,OutsourceRequestBusinessDO row) {
        editable(before);
        if(!Objects.equals(before.getProjectId(),row.getProjectId()) || !Objects.equals(before.getCode(),row.getCode()))throw new BusinessContractException("IDENTITY_IMMUTABLE","所属项目和申请单号不可修改");
        attachments.requireLegacyUnchanged(before.getAttachmentFiles(),row.getAttachmentFiles());
        if(!Objects.equals(before.getTriggerSource(),row.getTriggerSource()) || !Objects.equals(before.getTriggerRefId(),row.getTriggerRefId()))throw new BusinessContractException("SOURCE_IMMUTABLE","外包来源关联不可修改");
    }
    @Override protected void validateBusiness(OutsourceRequestBusinessDO row) {
        if(!defaults.validator().validate(BeanUtils.toBean(row,OutsourceRequestSaveReqVO.class)).isEmpty())throw new BusinessContractException("ENTITY_CONSTRAINT_INVALID","请填写完整的申请内容");
        var duplicate=nativeMapper.selectByCode(row.getCode());
        if(duplicate!=null && !Objects.equals(duplicate.getId(),row.getId()))throw new BusinessContractException("CODE_DUPLICATE","申请单号已存在");
    }
    @Override protected void afterUpdate(OutsourceRequestBusinessDO before,OutsourceRequestBusinessDO row){attachments.register(NativeAttachmentKind.OUTSOURCE,row.getId());}
    @Override protected void beforeDelete(OutsourceRequestBusinessDO row){editable(row);if("SITE_SURVEY".equals(row.getTriggerSource()))surveys.releaseDeletedOutsourceRequest(row.getTriggerRefId(),row.getProjectId(),row.getId(),deletionExecution.get());}
    @Override protected void validateDelivery(OutsourceRequestBusinessDO row,boolean write){if(write)editable(row);}
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("APPLICATION_APPROVED","申请已通过");}
    @Override protected Map<String,Boolean> runtimeFacts(OutsourceRequestBusinessDO row){return Map.of("APPLICATION_APPROVED",Objects.equals(row.getStatus(),3));}
    @Override protected boolean runtimeHandlingCompleted(OutsourceRequestBusinessDO row){return Objects.equals(row.getStatus(),3);}
    @Override protected LocalDateTime runtimeFormedAt(OutsourceRequestBusinessDO row){return row.getApproveTime();}
    private void editable(OutsourceRequestBusinessDO row){if(row.getStatus()==null || !Set.of(0,4).contains(row.getStatus()))throw new BusinessContractException("STATE_NOT_EDITABLE","只有草稿或已驳回的申请可以编辑");}
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix){return List.of(operation("submit","提交申请",prefix),operation("approve","办理申请",prefix),operation("withdraw","撤回申请",prefix),operation("terminate","终止申请",prefix));}
    private BusinessOperationDescriptor operation(String code,String name,String prefix){return new BusinessOperationDescriptor(code,1,name,BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update");}
    public BusinessOperationReceipt submit(Long id,Long version,String key){return businessAction("submit",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.submitOutsourceRequest(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt withdraw(Long id,Long version,String key){return businessAction("withdraw",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.withdrawOutsourceRequest(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt terminate(Long id,Long version,String key){return businessAction("terminate",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{nativeService.terminateOutsourceRequest(id);return mapper.selectById(id);});}
    public BusinessOperationReceipt approve(Long id,Long version,String key,String action,String opinion){
        if(action==null || !Set.of("PASS","REJECT","RETURN","TRANSFER","COUNTERSIGN").contains(action))throw new BusinessContractException("ACTION_INVALID","请选择有效的办理动作");
        var values=new LinkedHashMap<String,Object>();values.put("action",action);values.put("opinion",opinion);
        return businessAction("approve",id,version,key,values,ReceiptOutcome.SAVED,row->{var command=new OutsourceRequestApproveReqVO();command.setId(id);command.setApproveAction(action);command.setApproveOpinion(opinion);command.setApproverUserId(actor().userId());nativeService.approveOutsourceRequest(command);return mapper.selectById(id);});
    }
    @Override protected void beforeFormWrite(OutsourceRequestBusinessDO before,OutsourceRequestBusinessDO row,Map<String,Object> business) {
        if(!Set.of("siteSurveyExecution").containsAll(business.keySet()))throw new BusinessContractException("FIELD_NOT_OPEN","外包表单命令无效");
        if(business.containsKey("siteSurveyExecution"))row.setSurveyExecutionJson(JsonUtils.toJsonString(business.get("siteSurveyExecution")));
    }
    @Override protected void afterBodyPersisted(String operation,OutsourceRequestBusinessDO before,OutsourceRequestBusinessDO row) {
        if(before==null && "SITE_SURVEY".equals(row.getTriggerSource()))surveys.associateOutsourceRequest(row.getTriggerRefId(),row.getProjectId(),row.getId(),row.getSurveyExecutionJson()==null?null:JsonUtils.parseObject(row.getSurveyExecutionJson(),ProjectBusinessExecutionSelection.class));
    }
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public BusinessOperationReceipt deleteWithContext(Long id,Long version,String key,ProjectBusinessExecutionSelection execution) {
        deletionExecution.set(execution);try{return delete(id,version,key);}finally{deletionExecution.remove();}
    }
}
