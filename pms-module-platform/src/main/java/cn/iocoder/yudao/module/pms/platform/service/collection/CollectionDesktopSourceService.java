package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionRequestMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectionDesktopSourceService implements CollectionDesktopSourceApi {
    private final CollectionRequestMapper requests;
    private final CollectionTaskMapper tasks;
    private final CollectionTaskApi taskApi;
    private final List<CollectionSourceAdapter> adapters;
    private final CollectionAuthorization permissions;

    @Override @Transactional(rollbackFor=Exception.class)
    public Snapshot snapshot(TaskKey query) {
        requireTenant(query);
        var row=requests.findByTask(query.tenantId(),query.platformTaskId());
        if(row==null||!"DESKTOP".equals(row.getDispatchMode()))throw new CollectionOperationException("本机采集任务不存在");
        var task=taskApi.getTask(query.tenantId(),query.platformTaskId());
        var raw=tasks.selectByTenantAndPlatformTaskId(query.tenantId(),query.platformTaskId());
        if(task==null||raw==null||!task.credentialMode().startsWith("LOCAL_"))throw new CollectionOperationException("采集路径不匹配");
        if("LOCAL_SAVED".equals(task.credentialMode()))permissions.permission(row.getActorId(),"pms:device-credential:use");
        var matches=adapters.stream().filter(adapter->adapter.entry().equals(row.getEntry())).toList();
        if(matches.size()!=1||row.getDesktopSourceVersion()==null)throw new CollectionOperationException("本机采集来源无效");
        var authorization=CollectionSourceAdapter.AuthorizationQuery.builder().tenantId(query.tenantId()).actorId(row.getActorId())
                .objectId(row.getObjectId()).deviceId(Long.valueOf(task.deviceId())).access(CollectionSourceAdapter.Access.EXECUTE)
                .expectedVersion(row.getDesktopSourceVersion()).build();
        var source=matches.getFirst().authorize(authorization);
        if(!source.canExecute()||source.projectId()==null||source.deviceId()==null||!Objects.equals(source.projectId().toString(),task.projectId())
                ||!Objects.equals(source.deviceId().toString(),task.deviceId())||!Objects.equals(source.sourceContext(),task.sourceContext())
                ||!Objects.equals(source.sourceObjectType(),task.sourceObjectType()))throw new CollectionOperationException("采集来源已变化");
        matches.getFirst().authorizeConnection(CollectionSourceAdapter.PreparedConnection.builder().authorization(authorization).source(source)
                .host(task.host()).port(task.port()).protocol(task.protocol()).username(raw.getTemporaryUsername()).build());
        return new Snapshot(task,row.getActorId(),raw.getTemporaryUsername(),row.getCommandText(),row.getLocalConnectionId(),
                row.getLocalConnectionVersion(),row.getExecutionPurpose()==null?null:new CollectionExecutionProfile(row.getExecutionPurpose(),row.getCommandText()));
    }

    @Override @Transactional(rollbackFor=Exception.class)
    public void accepted(Acceptance command) {
        requireTenant(command.task());
        if(command.collectionId()==null||command.collectionId().isBlank()||command.collectionId().length()>128
                ||!Set.of("QUEUED","CONNECTING","EXECUTING","PARSING","SUCCEEDED","PARTIAL_SUCCESS","FAILED","TIMED_OUT","CANCELLED","SECURITY_EXCEPTION").contains(command.externalStatus()))
            throw new CollectionOperationException("本机接受证明无效");
        var key=command.task();var task=tasks.selectByTenantAndPlatformTaskIdForUpdate(key.tenantId(),key.platformTaskId());
        if(task==null||!task.getCredentialMode().startsWith("LOCAL_"))throw new CollectionOperationException("本机采集任务不存在");
        if(task.getExternalTaskId()!=null) {
            if(!Objects.equals(task.getExternalTaskId(),command.collectionId()))throw new CollectionOperationException("本机采集绑定冲突");
            return; // Acceptance retries cannot rewrite terminal history or callback observations.
        }
        String next=CollectionTaskStateMachine.acceptedDispatchStatus(task.getStatus());
        if(tasks.updateDispatchState(new CollectionTaskDispatchUpdate(key.tenantId(),key.platformTaskId(),task.getTechnicalStage(),
                next,"ACCEPTED",command.collectionId(),command.externalStatus(),null))!=1)throw new CollectionOperationException("采集状态已变化");
    }

    private void requireTenant(TaskKey query) {
        if(query==null||!Objects.equals(CollectionAuthorization.tenant(),query.tenantId())||query.platformTaskId()==null)
            throw new CollectionOperationException("本机采集租户无效");
    }
}
