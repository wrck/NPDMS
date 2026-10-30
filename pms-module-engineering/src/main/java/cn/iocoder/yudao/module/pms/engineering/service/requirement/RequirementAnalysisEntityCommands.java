package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

/** Existing native HTTP protocol delegates to the single public execution chain. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisEntityCommands {
    private final BusinessOperationDispatcher dispatcher;
    private final BusinessCallerContext callerContext;

    public EntityVersionProvider.Revision create(Create request, EntityActor actor, String key) {
        return nativeRevision(executeReceipt("create", null, null, request, actor, key));
    }
    public EntityVersionProvider.Revision save(RevisionRef ref, int version, Patch request, EntityActor actor, String key) {
        return nativeRevision(executeReceipt("save", ref, version, request, actor, key));
    }
    public EntityVersionProvider.Revision complete(RevisionRef ref, int version, Action request, EntityActor actor, String key) {
        return nativeRevision(executeReceipt("complete", ref, version, request, actor, key));
    }
    public EntityVersionProvider.Revision copy(RevisionRef ref, int version, Action request, EntityActor actor, String key) {
        return nativeRevision(executeReceipt("copy", ref, version, request, actor, key));
    }

    @SuppressWarnings("unchecked")
    public BusinessOperationReceipt executeReceipt(String code, RevisionRef ref, Integer version,
                                                   Object input, EntityActor actor, String key) {
        var caller=callerContext.require();
        if (!caller.tenantId().equals(actor.tenantId()) || !caller.userId().equals(actor.userId())) throw exception(FORBIDDEN);
        EntityDataRef target=null;
        if (ref != null) {
            actor.requireTenant(ref.entity());
            if (!"SOL".equals(ref.entity().ownerModule()) || !"REQUIREMENT_ANALYSIS".equals(ref.entity().entityType())) throw exception(FORBIDDEN);
            target=new EntityDataRef(new EntityRef(actor.tenantId(), "SOL", "requirementAnalysis", ref.entity().entityId()), ref.revisionId());
        }
        Map<String,Object> values=JsonUtils.parseObject(JsonUtils.toJsonString(input), Map.class);
        var result=dispatcher.dispatch(new BusinessOperationRequest(code,1,target,"SOL","requirementAnalysis",values,key,
                version == null ? null : version.longValue(),OperationEntryKind.INDEPENDENT,actor.correlationId()));
        return result;
    }

    public static EntityVersionProvider.Revision nativeRevision(BusinessOperationReceipt result) {
        return JsonUtils.parseObject(RequirementAnalysisBusinessApplicationService.revisionPayload(result),EntityVersionProvider.Revision.class);
    }

    public record Create(Long projectId, ProjectBusinessExecutionSelection execution) {}
    public record Patch(Map<String,Object> values, Long extensionDefinitionRevisionId, int expectedExtensionVersion,
                        Map<String,Object> extensionValues, ProjectBusinessExecutionSelection execution) {}
    public record Action(String reason, ProjectBusinessExecutionSelection execution) {}
}
