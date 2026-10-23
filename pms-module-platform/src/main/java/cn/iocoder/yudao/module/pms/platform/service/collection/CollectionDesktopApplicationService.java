package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsDesktopApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="pms.integration.device-ops",name="enabled",havingValue="true")
public class CollectionDesktopApplicationService {
    private final CollectionApplicationService application;
    private final CollectionAuthorization authorization;
    private final DeviceOpsDesktopApi desktop;

    @Transactional(rollbackFor=Exception.class)
    public Prepared prepare(Preparation input) {
        if(input.localConnectionId()!=null)authorization.permission(input.source().actorId(),"pms:device-credential:use");
        var execution=application.prepareDesktop(new CollectionApplicationService.DesktopPreparation(input.source(),input.request(),
                new CollectionApplicationService.LocalConnection(input.localConnectionId(),input.localConnectionVersion())));
        var ticket=desktop.register(new DeviceOpsDesktopApi.Registration(CollectionAuthorization.tenant(),input.source().actorId(),
                execution.task().projectId(),execution.task().platformTaskId()));
        return new Prepared(execution,ticket);
    }

    public DeviceOpsDesktopApi.Ticket access(Access input) {
        var source=application.context(input.source());
        String taskId=null;
        switch(input.operation()) {
            case "CONNECTIONS_READ" -> authorization.permission(input.source().actorId(),"pms:device-credential:use");
            case "CONNECTION_SAVE" -> {
                authorization.permission(input.source().actorId(),"pms:device-credential:create");
                if(!source.canExecute())throw new CollectionOperationException("当前来源不允许建立设备连接");
                authorization.project(input.source().actorId(),source.projectId(),true,false);
            }
            case "TASK_READ" -> {
                var execution=application.readDesktopExecution(new CollectionApplicationService.DesktopExecutionQuery(input.source(),input.executionId()));
                taskId=execution.task().platformTaskId();
            }
            default -> throw new CollectionOperationException("本机操作无效");
        }
        return desktop.access(new DeviceOpsDesktopApi.Access(CollectionAuthorization.tenant(),input.source().actorId(),
                source.projectId().toString(),input.operation(),taskId));
    }

    @Transactional(rollbackFor=Exception.class)
    public Prepared resume(Resume input) {
        var execution=application.readDesktopExecution(new CollectionApplicationService.DesktopExecutionQuery(input.source(),input.executionId()));
        if(!java.util.Objects.equals(execution.actorId(),input.source().actorId()))throw new CollectionOperationException("只能继续本人发起的本机采集");
        var ticket=desktop.register(new DeviceOpsDesktopApi.Registration(CollectionAuthorization.tenant(),input.source().actorId(),
                execution.task().projectId(),execution.task().platformTaskId()));
        return new Prepared(execution,ticket);
    }

    public record Preparation(CollectionApplicationApi.SourceQuery source,CollectionExecutionRequest request,
                              String localConnectionId,Long localConnectionVersion){}
    public record Access(CollectionApplicationApi.SourceQuery source,String operation,Long executionId){}
    public record Resume(CollectionApplicationApi.SourceQuery source,Long executionId){}
    public record Prepared(CollectionApplicationApi.Execution execution,DeviceOpsDesktopApi.Ticket ticket){}
}
