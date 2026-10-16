package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.*;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.comment.BpmCommentService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;
import java.time.ZoneId;
import java.util.*;

/** A shared adapter onto native BPM. No copy of BPM authorization, candidate or decision rules. */
@Service @RequiredArgsConstructor
public class PmsDefaultBusinessApprovalProcess implements DefaultBusinessApprovalProcessApi {
    static final String BUSINESS_KEY_PREFIX="pms-default-business:";
    static final String VARIABLE_PREFIX="pmsBusiness";
    private final BpmProcessInstanceServiceImpl processes;
    private final BpmProcessDefinitionService definitions;
    private final RepositoryService repository;
    private final HistoryService history;
    private final BpmCommentService comments;
    private final ObjectProvider<DefaultBusinessApprovalApi> associations;

    @Override @DataPermission(enable=false)
    public List<Definition> definitions(Long actorId) {
        return repository.createProcessDefinitionQuery().processDefinitionTenantId(FlowableUtils.getTenantId()).active().latestVersion().list().stream()
                .filter(row->{var info=definitions.getProcessDefinitionInfo(row.getId());return info!=null && !Boolean.FALSE.equals(info.getVisible()) && definitions.canUserStartProcessDefinition(info,actorId);})
                .map(row->view(row,definitions.getProcessDefinitionInfo(row.getId()))).toList();
    }
    @Override @DataPermission(enable=false)
    public Preview preview(Command command) {
        validate(command.context());var definition=definition(command.definitionId(),command.context().actorId());
        var detail=processes.getApprovalDetail(command.context().actorId(),new BpmApprovalDetailReqVO()
                .setProcessDefinitionId(definition.getId()).setActivityId("StartUserNode").setProcessVariables(variables(command)));
        return new Preview(detail.getActivityNodes()==null?List.of():detail.getActivityNodes().stream().map(node->new Node(node.getId(),node.getName(),
                Objects.equals(node.getCandidateStrategy(),BpmTaskCandidateStrategyEnum.START_USER_SELECT.getStrategy()))).toList(),
                detail.getFormFieldsPermission()==null?Map.of():detail.getFormFieldsPermission());
    }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class) @DataPermission(enable=false)
    public Result start(Command command) {
        validate(command.context());var definition=definition(command.definitionId(),command.context().actorId());
        var variables=variables(command);
        String id=FlowableUtils.executeAuthenticatedUserId(command.context().actorId(),()->processes.createProcessInstance0(command.context().actorId(),definition,
                variables,BUSINESS_KEY_PREFIX+UUID.randomUUID(),command.selectedApprovers()));
        return fact(instance(command.context(),id));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class) @DataPermission(enable=false)
    public Result cancel(Context context,String instanceId,String reason,Long actorId) {
        validate(context);instance(context,instanceId);
        processes.cancelProcessInstanceByStartUser(actorId,new BpmProcessInstanceCancelReqVO().setId(instanceId).setReason(reason));
        return fact(instance(context,instanceId));
    }
    @Override public Detail detail(Context context,String instanceId) {
        var row=instance(context,instanceId);
        @SuppressWarnings("unchecked") Map<String,Object> values=JsonUtils.parseObject((String)row.getProcessVariables().get(VARIABLE_PREFIX+"Snapshot"),Map.class);
        var opinions=comments.getCommentListByProcessInstanceId(instanceId).stream()
                .sorted(Comparator.comparing(org.flowable.engine.task.Comment::getTime)).map(comment->new Opinion(comment.getUserId(),comment.getType(),comment.getFullMessage(),
                        comment.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())).toList();
        return new Detail(row.getName(),values,opinions);
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void completed(BpmProcessInstanceStatusEvent event) {
        if(event.getBusinessKey()==null || !event.getBusinessKey().startsWith(BUSINESS_KEY_PREFIX))return;
        var row=load(event.getId());
        if(row==null || row.getEndTime()==null || !Objects.equals(row.getBusinessKey(),event.getBusinessKey()))throw invalid("流程历史尚未形成");
        var result=fact(row);
        if(!Objects.equals(status(event.getStatus()),result.status()))throw invalid("流程结果与历史不一致");
        associations.getObject().record(result);
    }

    private ProcessDefinition definition(String id,Long actorId) {
        var row=repository.createProcessDefinitionQuery().processDefinitionTenantId(FlowableUtils.getTenantId()).processDefinitionId(id).active().singleResult();
        if(row==null)throw invalid("流程已停用或不存在");
        var info=definitions.getProcessDefinitionInfo(id);
        if(info==null || !definitions.canUserStartProcessDefinition(info,actorId))throw invalid("无权发起该流程");
        var definition=view(row,info);if(!definition.available())throw invalid(definition.unavailableReason());
        return row;
    }
    private Definition view(ProcessDefinition row,BpmProcessDefinitionInfoDO info) {
        boolean specialized=Objects.equals(info.getFormType(),BpmModelFormTypeEnum.CUSTOM.getType())
                && info.getFormCustomCreatePath()!=null && !info.getFormCustomCreatePath().isBlank();
        return new Definition(row.getId(),row.getKey(),row.getName(),row.getVersion(),info.getFormConf(),info.getFormFields()==null?List.of():info.getFormFields(),
                !specialized,specialized?"该流程使用专用业务表单，请从对应业务入口发起":null);
    }
    private Map<String,Object> variables(Command command) {
        var values=new HashMap<String,Object>();if(command.variables()!=null)values.putAll(command.variables());
        if(values.keySet().stream().anyMatch(key->key.startsWith("pms")))throw invalid("业务关联信息由服务端提供");
        values.putAll(identity(command.context()));
        values.put(VARIABLE_PREFIX+"Snapshot",JsonUtils.toJsonString(command.values()));
        return values;
    }
    private Map<String,Object> identity(Context context) {
        var entity=context.entity();return Map.of(VARIABLE_PREFIX+"Tenant",entity.tenantId(),VARIABLE_PREFIX+"Owner",entity.ownerModule(),
                VARIABLE_PREFIX+"Type",entity.entityType(),VARIABLE_PREFIX+"Id",entity.entityId(),VARIABLE_PREFIX+"Project",context.projectId(),
                VARIABLE_PREFIX+"Version",context.version(),VARIABLE_PREFIX+"Attempt",context.attemptId(),VARIABLE_PREFIX+"Actor",context.actorId());
    }
    private HistoricProcessInstance instance(Context context,String id) {
        validate(context);var row=load(id);
        if(row==null || row.getBusinessKey()==null || !row.getBusinessKey().startsWith(BUSINESS_KEY_PREFIX)
                || identity(context).entrySet().stream().anyMatch(entry->!Objects.equals(entry.getValue(),row.getProcessVariables().get(entry.getKey()))))
            throw invalid("流程不属于当前业务提交");
        return row;
    }
    private HistoricProcessInstance load(String id) {
        return history.createHistoricProcessInstanceQuery().processInstanceTenantId(FlowableUtils.getTenantId()).processInstanceId(id).includeProcessVariables().singleResult();
    }
    private Result fact(HistoricProcessInstance row) {
        var v=row.getProcessVariables();
        var entity=new EntityRef(number(v,"Tenant"),(String)v.get(VARIABLE_PREFIX+"Owner"),(String)v.get(VARIABLE_PREFIX+"Type"),number(v,"Id"));
        var context=new Context(entity,number(v,"Project"),number(v,"Version"),(String)v.get(VARIABLE_PREFIX+"Attempt"),number(v,"Actor"));
        validate(context);
        String state=status((Integer)v.get("PROCESS_STATUS"));
        if(row.getEndTime()!=null && "PENDING".equals(state))throw invalid("已结束流程缺少真实审批结论");
        return new Result(context,row.getProcessDefinitionId(),row.getId(),row.getEndTime()==null?"PENDING":state,(String)v.get("PROCESS_REASON"));
    }
    private Long number(Map<String,Object> values,String key) {
        var value=values.get(VARIABLE_PREFIX+key);if(!(value instanceof Long))throw invalid("审批业务身份缺失");return (Long)value;
    }
    private String status(Integer value){return switch(value==null?0:value){case 1->"PENDING";case 2->"APPROVED";case 3->"REJECTED";case 4->"WITHDRAWN";default->throw invalid("审批状态无法确认");};}
    private void validate(Context context) {
        if(context==null || context.entity()==null || !Objects.equals(context.entity().tenantId(),TenantContextHolder.getRequiredTenantId())
                || TenantContextHolder.isIgnore() || context.actorId()==null || context.actorId()<=0 || context.projectId()==null || context.projectId()<=0
                || context.entity().entityId()==null || context.entity().entityId()<=0 || context.version()==null || context.version()<0
                || context.attemptId()==null || context.attemptId().isBlank())throw invalid("审批业务范围无效");
    }
    private IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
}
