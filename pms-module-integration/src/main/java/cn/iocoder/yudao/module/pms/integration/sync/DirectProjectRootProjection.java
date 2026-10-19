package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectProjectRootMapper;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import java.util.Objects;

/** Only the newly created historical root; never republish or replace an existing tree. */
@RequiredArgsConstructor
final class DirectProjectRootProjection {
    record Root(Long tenantId,Long projectId,Long actorId) {}
    record Projection(Long rootProjectId,long treeVersion,int nodeCount,int pathCount) {}
    private final DirectProjectRootMapper mapper;
    private final PlatformBusinessEventApi events;
    Projection publish(Root root) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("DPPMS_PROJECT_ROOT_TRANSACTION_REQUIRED");
        var query=new DirectProjectRootMapper.RootQuery(root.tenantId(),root.projectId());
        var row=mapper.lockRoot(query);
        if(row==null || row.getParentId()!=null || !Objects.equals(row.getRootId(),root.projectId())
                || !Objects.equals(row.getTenantId(),root.tenantId()) || mapper.selectActiveVersion(query)!=null)
            throw new IllegalStateException("DPPMS_PROJECT_NEW_ROOT_CHANGED");
        var nodes=mapper.selectRootNodes(query);
        if(nodes.size()!=1 || !Objects.equals(nodes.getFirst().getId(),root.projectId()))
            throw new IllegalStateException("DPPMS_PROJECT_NEW_ROOT_NOT_SINGLE");
        String change="DPPMS_PROJECT_CREATE:"+root.projectId();
        var version=new DirectProjectRootMapper.Version();
        version.setTenantId(root.tenantId());version.setRootProjectId(root.projectId());version.setActor(root.actorId().toString());
        version.setChangeBatchId(change);
        if(mapper.insertBuilding(version)!=1 || version.getId()==null
                || mapper.insertSelfPath(new DirectProjectRootMapper.SelfPath(IdWorker.getId(),root.tenantId(),root.projectId(),root.actorId().toString()))!=1
                || mapper.activate(version)!=1)throw new IllegalStateException("DPPMS_PROJECT_ROOT_PROJECTION_WRITE_CONFLICT");
        var payload=ProjectRuleReevaluationRequested.create(root.tenantId(),root.projectId(),null,"PROJECT_TREE:"+change);
        events.append("Project",root.projectId().toString(),new BusinessEvent(payload.eventId(),ProjectRuleReevaluationRequested.EVENT_TYPE,JsonUtils.toJsonString(payload)));
        return new Projection(root.projectId(),1,1,1);
    }
}
