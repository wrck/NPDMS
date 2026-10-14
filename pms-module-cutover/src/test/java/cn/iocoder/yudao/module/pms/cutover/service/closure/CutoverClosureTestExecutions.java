package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverCollectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.framework.adapter.CutoverCollectionSources;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.RequestClosureCollectionCommand;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverCommandExecutor;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverWriteIntent;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionApplicationApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionExecutionQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Materializes a controlled platform execution, then uses the current production link service. */
final class CutoverClosureTestExecutions {
    private final CutoverTaskMapper tasks;
    private final CutoverClosureCollectionPort external;
    private final CutoverClosureExecutionLinkService links;
    private CollectionApplicationApi.Execution current;

    CutoverClosureTestExecutions(CutoverTaskMapper tasks, CutoverClosureMapper closures,
                                CutoverCollectionEvidenceMapper evidence, CutoverProjectScopePort scopes,
                                PlatformCommandExecutionApi platform, CutoverClosureCollectionPort external) {
        this.tasks = tasks;
        this.external = external;
        var factory = new StaticListableBeanFactory();
        factory.addBean("execution", (CollectionExecutionQueryApi) query -> current);
        links = new CutoverClosureExecutionLinkService(tasks, closures, evidence, scopes,
                factory.getBeanProvider(CollectionExecutionQueryApi.class), new CutoverCommandExecutor(platform));
    }

    void link(RequestClosureCollectionCommand request) {
        var task = tasks.selectById(request.taskId());
        var identity = new CutoverClosureCollectionPort.CollectionIntentIdentity(request.tenantId(), request.taskId(),
                request.closureId(), request.deviceId(), request.collectionStage(), request.idempotencyKey());
        var fact = external.request(new CutoverClosureCollectionPort.CollectionRequest(identity, request.actorId(),
                task.getProjectId(), request.authentication(), request.templateCode(), request.templateVersion(), request.correlationId()));
        long executionId = Integer.toUnsignedLong(fact.collectionTaskId().hashCode()) + 1;
        String status = fact.outcome() == CutoverClosureCollectionPort.DispatchOutcome.FAILED ? "FAILED" : "RUNNING";
        var source = new CollectionTaskDTO(executionId, null, fact.collectionTaskId(), "CUT", "CutoverClosure",
                request.closureId().toString(), task.getProjectId().toString(), request.deviceId().toString(), "设备",
                null, null, null, null, null, null, "SAVED", null, null, request.idempotencyKey(), "COLLECT", status,
                "DISPATCH", null, null, null, null, null, null, fact.failureCode());
        current = new CollectionApplicationApi.Execution(executionId, request.actorId(), fact.occurredAt(), null,
                source, null, "采集", null);
        String entry = CutoverCollectionSources.CLOSURE_STAGES.entrySet().stream()
                .filter(value -> value.getValue().equals(request.collectionStage().name())).findFirst().orElseThrow().getKey();
        links.link(new CutoverClosureExecutionLinkService.Command(request.tenantId(), request.actorId(), request.taskId(),
                request.closureId(), request.expectedTaskVersion(), request.expectedClosureVersion(), entry, executionId,
                new CutoverWriteIntent(request.idempotencyKey(), request.correlationId())));
    }
}
