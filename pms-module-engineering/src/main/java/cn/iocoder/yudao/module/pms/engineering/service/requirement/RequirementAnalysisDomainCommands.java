package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntitySaveSupport;
import static cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisEntityCommands.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_VERSION_NOT_MATCH;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.*;

/** Native Owner content/lifecycle policy; the common application service owns the execution ledger. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisDomainCommands {
    private final RequirementAnalysisEntityProvider provider;
    private final RequirementAnalysisAccess access;
    private final EntityVersionApi versions;
    private final EntityExtensionApi extensions;


    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision create(Create request, EntityActor actor, String key) {
        return execute("RA_ENTITY_CREATE", key, request, actor, () -> ProjectOwnerOperationScope.call(
                "SOL", "REQUIREMENT_ANALYSIS", () -> declaration("SOL.REQUIREMENT_ANALYSIS.CREATE",
                        request.projectId(), null, actor, request.execution()),
                () -> provider.createInitial(request.projectId(), actor, request.execution())));
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision save(RevisionRef ref, int expectedVersion, Patch request, EntityActor actor, String key) {
        return execute("RA_ENTITY_SAVE", key, List.of(ref, expectedVersion, request), actor, () ->
                inRevisionOperation("SOL.REQUIREMENT_ANALYSIS.SAVE", ref, actor, request.execution(), () -> {
            access.lock(ref.revisionId(), expectedVersion, actor, request.execution(), true);
            var target = EntityDataRef.revision(ref);
            if (request.extensionValues() != null) {
                if (request.extensionValues().keySet().stream().anyMatch(code -> RequirementAnalysisFields.property(code) != null))
                    throw exception(cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_CONTENT_INVALID);

            }
            // Every business save advances the revision lock, even if only extensions changed.
            var extensionPatch = new BusinessEntitySaveSupport.ExtensionPatch(request.extensionDefinitionRevisionId(),
                    request.expectedExtensionVersion(), request.extensionValues());
            return new BusinessEntitySaveSupport(extensions).save(target, actor, (long) expectedVersion, extensionPatch,
                    () -> provider.save(ref, expectedVersion, request.values() == null ? Map.of() : request.values(), actor));
        }));
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision complete(RevisionRef ref, int expectedVersion, Action request, EntityActor actor, String key) {
        return execute("RA_ENTITY_COMPLETE", key, List.of(ref, expectedVersion, request), actor, () ->
                inRevisionOperation("SOL.REQUIREMENT_ANALYSIS.COMPLETE", ref, actor, request.execution(), () -> {
            access.lock(ref.revisionId(), expectedVersion, actor, request.execution(), true);
            return versions.complete(ref, expectedVersion, actor);
        }));
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision copy(RevisionRef ref, int expectedVersion, Action request, EntityActor actor, String key) {
        return execute("RA_ENTITY_COPY", key, List.of(ref, expectedVersion, request), actor, () ->
                inRevisionOperation("SOL.REQUIREMENT_ANALYSIS.COPY", ref, actor, request.execution(), () -> {
            var source = access.lock(ref.revisionId(), expectedVersion, actor, request.execution(), false);
            if (!ref.equals(source.revisionRef())) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
            return versions.create(ref.entity(), ref, request.reason(), actor);
        }));
    }

    private EntityVersionProvider.Revision inRevisionOperation(String code, RevisionRef ref, EntityActor actor,
            ProjectBusinessExecutionSelection selection, Supplier<EntityVersionProvider.Revision> work) {
        return ProjectOwnerOperationScope.call("SOL", "REQUIREMENT_ANALYSIS", () -> {
            // The actual business row, not the outer execution frame, supplies project and source identity.
            var source = access.read(ref.revisionId(), actor);
            if (source == null || !ref.equals(source.revisionRef())) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
            return declaration(code, source.getProjectId(), source.getId().toString(), actor, selection);
        }, work);
    }

    private ProjectOwnerOperationScope.Declaration declaration(String code, Long projectId, String sourceId,
            EntityActor actor, ProjectBusinessExecutionSelection selection) {
        return new ProjectOwnerOperationScope.Declaration(actor.tenantId(), actor.userId(), projectId,
                "SOL", "REQUIREMENT_ANALYSIS", code, 1, sourceId, selection);
    }

    private EntityVersionProvider.Revision execute(String scope, String key, Object request, EntityActor actor,
                                                    Supplier<EntityVersionProvider.Revision> operation) {
        return operation.get();
    }
}
