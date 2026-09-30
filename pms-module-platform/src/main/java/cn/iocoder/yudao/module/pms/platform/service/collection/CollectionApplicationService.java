package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionRequestPageQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter.Access.*;
@Service @RequiredArgsConstructor
public class CollectionApplicationService implements CollectionApplicationApi {
    private final List<CollectionSourceAdapter> sources;
    private final CollectionRequestMapper requests;
    private final CollectionTaskMapper taskMapper;
    private final CollectionTaskApi tasks;
    private final CollectionBusinessResultDeliveryService resultDelivery;
    private final CollectionLogAccessApi logs;
    private final CollectionTemplateService templates;
    private final CollectionConnectionService connections;
    private final ProjectDeviceSelectionApi devices;
    private final ObjectProvider<CollectionDispatchApi> dispatcher;
    private final PlatformTransactionManager transactions;

    @Override public CollectionSourceAdapter.Source context(String entry,Long id,Long actor) {
        return source(entry).authorize(tenant(),actor,id,null,READ,null);
    }
    @Override public Execution submit(String entry,Long id,Long actor,CollectionExecutionRequest request) {
        try {
            if(request==null||request.getRequestKey()==null||!request.getRequestKey().matches("[a-zA-Z0-9-]{16,64}"))throw new CollectionOperationException("请求标识无效");
            var dispatch=dispatch();
            Prepared prepared=new TransactionTemplate(transactions).execute(tx->prepare(entry,id,actor,request));
            if(prepared.fresh()) {
                try {
                    var task=tasks.getTask(tenant(),prepared.row().getPlatformTaskId());
                    var commands=prepared.row().getCommandText().lines().toList();
                    if(prepared.saved()!=null) {
                        var saved=prepared.saved().connection();
                        dispatch.dispatchSaved(new CollectionDispatchApi.SavedCommand(tenant(),task.platformTaskId(),commands,
                                saved.username(),saved.id(),saved.version(),request.getRequestKey()));
                    } else dispatch.dispatchManual(new CollectionDispatchApi.Command(tenant(),task.platformTaskId(),commands,
                            request.getUsername(),request.getPassword(),request.getRequestKey()));
                } catch(RuntimeException failed) {
                    var current=tasks.getTask(tenant(),prepared.row().getPlatformTaskId());
                    if("PENDING_DISPATCH".equals(current.technicalStage()))throw new CollectionOperationException("采集已记录但未能下发，请查看执行历史");
                }
            }
            return view(prepared.row());
        } finally {if(request!=null&&request.getPassword()!=null)Arrays.fill(request.getPassword(),'\0');}
    }
    private Prepared prepare(String entry,Long id,Long actor,CollectionExecutionRequest r) {
        var adapter=source(entry);
        var currentSource=adapter.authorize(tenant(),actor,id,r.getDeviceId(),CANCEL,null);
        String digest=digest(entry,id,actor,r);
        var old=requests.findRequest(tenant(),r.getRequestKey());
        if(old!=null) {
            if(!entry.equals(old.getEntry())||!id.equals(old.getObjectId())||!digest.equals(old.getRequestDigest()))throw new CollectionOperationException("同一请求内容已变化，请新建执行请求");
            checkHistory(currentSource,task(old));return new Prepared(old,false,null);
        }
        var source=adapter.authorize(tenant(),actor,id,r.getDeviceId(),EXECUTE,r.getExpectedVersion());
        // The source/project lock serializes submissions. Use a current read after waiting, even under MySQL repeatable-read.
        var concurrent=requests.lockRequest(tenant(),r.getRequestKey());
        if(concurrent!=null) {
            if(!entry.equals(concurrent.getEntry())||!id.equals(concurrent.getObjectId())||!digest.equals(concurrent.getRequestDigest()))throw new CollectionOperationException("同一请求内容已变化，请新建执行请求");
            var winner=taskMapper.selectByTenantAndPlatformTaskIdForUpdate(tenant(),concurrent.getPlatformTaskId());
            if(winner==null||!id.toString().equals(winner.getSourceObjectId())
                    ||!source.projectId().toString().equals(winner.getProjectId())
                    ||!source.sourceContext().equals(winner.getSourceContext())
                    ||!source.sourceObjectType().equals(winner.getSourceObjectType()))throw new CollectionOperationException("业务记录归属已变化，原采集记录不可在新项目读取");
            return new Prepared(concurrent,false,null);
        }
        if(!source.canExecute()||source.deviceId()==null)throw new CollectionOperationException("当前业务记录不能采集或尚未选择设备");
        var selected=devices.validateSelection(source.projectId(),List.of(source.deviceId())).getFirst();
        if(r.getProtocol()==null||!Set.of("SSH","TELNET").contains(r.getProtocol()))throw new CollectionOperationException("请选择有效连接协议");
        CollectionTemplateDO template=null;
        String commands;
        if(r.getTemplateId()!=null) {
            template=templates.forExecution(actor,r.getTemplateId(),entry,r.getProtocol(),selected.productModel());
            commands=template.getCommandText();
        }else{
            if(!source.manualAllowed())throw new CollectionOperationException("此入口必须使用发布模板");
            commands=CollectionTemplateService.normalize(r.getCommands());
        }
        CollectionConnectionService.Resolved saved=null;
        String host=r.getHost(),username=r.getUsername();Integer port=r.getPort();
        if(r.getCredentialId()!=null) {
            if(r.getPassword()!=null&&r.getPassword().length>0)throw new CollectionOperationException("已保存连接与临时密码不能混用");
            saved=connections.resolve(actor,r.getCredentialId(),source.projectId(),source.deviceId(),r.getProtocol(),r.getTemplateId());
            host=saved.connection().host();port=saved.connection().port();username=saved.connection().username();
        }else if(host==null||!host.matches("[a-zA-Z0-9.:-]{1,253}")||port==null||port<1||port>65535||username==null||username.isBlank()||username.length()>128||r.getPassword()==null||r.getPassword().length==0||r.getPassword().length>4096)throw new CollectionOperationException("请填写有效的设备地址和临时凭证");
        if(r.getRetryOfId()!=null) {
            var previous=required(entry,id,r.getRetryOfId());var previousTask=task(previous);
            if(!source.deviceId().toString().equals(previousTask.deviceId())||Set.of("CREATED","AUTHORIZED","DISPATCHED","EXECUTING","CALLBACK_PROCESSING").contains(previousTask.status()))throw new CollectionOperationException("原任务仍在执行或设备已变化，不能重新执行");
        }
        String templateId=template==null?"manual-"+id:template.getId().toString();
        String templateVersion=template==null?r.getRequestKey():template.getRevision().toString();
        var batch=tasks.createBatch(new CollectionBatchCreateCommand(tenant(),actor,"unified-"+r.getRequestKey(),digest,
                source.sourceContext(),source.sourceObjectType(),id.toString(),source.projectId().toString(),source.completionMode(),
                List.of(new CollectionTaskCreateItem(source.deviceId().toString(),selected.name(),host,port,r.getProtocol(),templateId,
                        templateVersion,CollectionTemplateService.hash(commands),saved==null?"TEMPORARY_SECRET":"SAVED_CREDENTIAL",
                        saved==null?null:r.getCredentialId(),saved==null?null:saved.grantId(),r.getRequestKey(),source.sourceContext(),source.sourceObjectType(),id.toString()))));
        var platform=batch.tasks().getFirst();
        var task=taskMapper.selectByTenantAndPlatformTaskId(tenant(),platform.platformTaskId());
        task.setTemporaryUsername(username);taskMapper.updateById(task);
        var row=new CollectionRequestDO();row.setTenantId(tenant());row.setEntry(entry);row.setObjectId(id);row.setActorId(actor);
        row.setCreator(actor.toString());row.setRequestKey(r.getRequestKey());row.setRequestDigest(digest);row.setPlatformTaskId(platform.platformTaskId());
        row.setCommandText(commands);row.setTemplateName(template==null?"手工命令":template.getName());row.setTemplateRevisionId(r.getTemplateId());
        row.setCredentialVersion(saved==null?null:saved.connection().version());row.setRetryOfId(r.getRetryOfId());requests.insert(row);
        return new Prepared(row,true,saved);
    }
    @Override public PageResult<Execution> page(String entry,Long id,Long actor,int pageNo,int pageSize) {
        var current=context(entry,id,actor);if(pageNo<1||pageSize<1||pageSize>50)throw new CollectionOperationException("分页参数无效");
        var page=requests.page(new CollectionRequestPageQuery(tenant(),entry,id,pageNo,pageSize));
        return new PageResult<>(page.getList().stream().map(row->{checkHistory(current,task(row));return view(row);}).toList(),page.getTotal());
    }
    @Override public Execution findByRequestKey(String entry,Long id,Long actor,String key) {
        var current=context(entry,id,actor);if(key==null||!key.matches("[a-zA-Z0-9-]{16,64}"))throw new CollectionOperationException("请求标识无效");
        var row=requests.findRequest(tenant(),key);if(row==null||!entry.equals(row.getEntry())||!id.equals(row.getObjectId()))return null;
        checkHistory(current,task(row));return view(row);
    }
    @Override public Execution consume(String entry,Long id,Long actor,Long executionId) {
        var row=required(entry,id,executionId);var task=task(row);
        var source=source(entry).authorize(tenant(),actor,id,Long.valueOf(task.deviceId()),CONSUME,null);checkBinding(source,task);
        if(!"BUSINESS_CONSUMPTION".equals(task.completionMode()))throw new CollectionOperationException("独立采集无业务关联动作");
        if(task.fileVersionId()==null)throw new CollectionOperationException("尚无可回传的日志");
        resultDelivery.deliver(tenant(),task.platformTaskId());
        return view(required(entry,id,executionId));
    }
    @Override public void cancel(String entry,Long id,Long actor,Long executionId) {
        var task=task(required(entry,id,executionId));
        var source=source(entry).authorize(tenant(),actor,id,Long.valueOf(task.deviceId()),CANCEL,null);checkBinding(source,task);dispatch().cancel(tenant(),task.platformTaskId());
    }
    @Override public String download(String entry,Long id,Long actor,Long executionId) {
        var task=task(required(entry,id,executionId));
        var source=source(entry).authorize(tenant(),actor,id,Long.valueOf(task.deviceId()),READ,null);checkBinding(source,task);return logs.download(tenant(),actor,task.platformTaskId());
    }
    @Override public List<Map<String,Object>> semanticResults(String entry,Long id,Long actor,Long executionId) {
        var task=task(required(entry,id,executionId));
        var source=source(entry).authorize(tenant(),actor,id,Long.valueOf(task.deviceId()),READ,null);checkBinding(source,task);
        return dispatch().semanticResults(tenant(),task.platformTaskId());
    }
    private CollectionRequestDO required(String entry,Long id,Long executionId){var r=requests.selectById(executionId);if(r==null||!tenant().equals(r.getTenantId())||!entry.equals(r.getEntry())||!id.equals(r.getObjectId()))throw new CollectionOperationException("执行记录不存在");return r;}
    private CollectionTaskDTO task(CollectionRequestDO r){var t=tasks.getTask(tenant(),r.getPlatformTaskId());if(t==null||!r.getObjectId().toString().equals(t.sourceObjectId()))throw new CollectionOperationException("采集任务绑定异常");return t;}
    private void checkBinding(CollectionSourceAdapter.Source s,CollectionTaskDTO t){if(!s.projectId().toString().equals(t.projectId())||!s.sourceContext().equals(t.sourceContext())||!s.sourceObjectType().equals(t.sourceObjectType())||s.deviceId()==null||!s.deviceId().toString().equals(t.deviceId()))throw new CollectionOperationException("业务记录的项目或设备已变化，不能操作原采集结果");}
    private Execution view(CollectionRequestDO r){return new Execution(r.getId(),r.getActorId(),r.getCreateTime(),r.getConsumedResultVersion(),task(r),r.getCommandText(),r.getTemplateName(),r.getRetryOfId());}
    private void checkHistory(CollectionSourceAdapter.Source source,CollectionTaskDTO task){if(!source.projectId().toString().equals(task.projectId())||!source.sourceContext().equals(task.sourceContext())||!source.sourceObjectType().equals(task.sourceObjectType()))throw new CollectionOperationException("业务记录归属已变化，原采集记录不可在新项目读取");}
    private CollectionSourceAdapter source(String entry){var matches=sources.stream().filter(s->s.entry().equals(entry)).toList();if(matches.size()!=1)throw new CollectionOperationException("该采集入口尚未接入");return matches.getFirst();}
    private CollectionDispatchApi dispatch(){var d=dispatcher.getIfAvailable();if(d==null)throw new CollectionOperationException("DAC 尚未启用");return d;}
    private Long tenant(){return CollectionAuthorization.tenant();}
    private String digest(String entry,Long id,Long actor,CollectionExecutionRequest r){
        String commands=r.getTemplateId()==null?CollectionTemplateService.normalize(r.getCommands()):"";
        if("configuration".equals(entry)&&r.getTemplateId()==null&&r.getCredentialId()==null&&r.getRetryOfId()==null)
            return CollectionTemplateService.hash(JsonUtils.toJsonString(Arrays.asList(id,actor,r.getExpectedVersion(),r.getHost(),r.getPort(),r.getProtocol(),r.getUsername(),CollectionTemplateService.hash(commands))));
        return CollectionTemplateService.hash(JsonUtils.toJsonString(Arrays.asList(entry,id,actor,r.getExpectedVersion(),r.getDeviceId(),r.getHost(),r.getPort(),r.getProtocol(),r.getUsername(),r.getTemplateId(),r.getCredentialId(),CollectionTemplateService.hash(commands),r.getRetryOfId())));
    }
    private record Prepared(CollectionRequestDO row,boolean fresh,CollectionConnectionService.Resolved saved){}
}
