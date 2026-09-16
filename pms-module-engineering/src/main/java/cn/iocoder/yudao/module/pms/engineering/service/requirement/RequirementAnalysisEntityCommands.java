package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_VERSION_NOT_MATCH;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.*;

/** One Owner transaction per browser command, including common capabilities and existing idempotency. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisEntityCommands {
    private final RequirementAnalysisEntityProvider provider;
    private final RequirementAnalysisAccess access;
    private final EntityVersionApi versions;
    private final EntityExtensionApi extensions;
    private final PlatformCommandExecutionApi commands;

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision create(Create request, EntityActor actor, String key) {
        return execute("RA_ENTITY_CREATE", key, request, actor, () -> provider.createInitial(request.projectId(), actor, request.execution()));
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision save(RevisionRef ref, int expectedVersion, Patch request, EntityActor actor, String key) {
        return execute("RA_ENTITY_SAVE", key, List.of(ref, expectedVersion, request), actor, () -> {
            access.lock(ref.revisionId(), expectedVersion, actor, request.execution(), true);
            var target = EntityDataRef.revision(ref);
            if (request.extensionValues() != null) {
                extensions.save(new EntityExtensionApi.Save(target, actor, expectedVersion, request.expectedExtensionVersion(),
                        request.extensionDefinitionRevisionId(), request.extensionValues()));
            }
            // Every business save advances the revision lock, even if only extensions changed.
            return provider.save(ref, expectedVersion, request.values() == null ? Map.of() : request.values(), actor);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision complete(RevisionRef ref, int expectedVersion, Action request, EntityActor actor, String key) {
        return execute("RA_ENTITY_COMPLETE", key, List.of(ref, expectedVersion, request), actor, () -> {
            access.lock(ref.revisionId(), expectedVersion, actor, request.execution(), true);
            return versions.complete(ref, expectedVersion, actor);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision copy(RevisionRef ref, int expectedVersion, Action request, EntityActor actor, String key) {
        return execute("RA_ENTITY_COPY", key, List.of(ref, expectedVersion, request), actor, () -> {
            var source = access.lock(ref.revisionId(), expectedVersion, actor, request.execution(), false);
            if (!ref.equals(source.revisionRef())) throw exception(REQUIREMENT_VERSION_NOT_MATCH);
            return versions.create(ref.entity(), ref, request.reason(), actor);
        });
    }

    private EntityVersionProvider.Revision execute(String scope, String key, Object request, EntityActor actor,
                                                   Supplier<EntityVersionProvider.Revision> operation) {
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(actor.tenantId(), scope, actor.userId(), key),
                digest(request), EntityVersionProvider.Revision.class, operation,
                response -> new PlatformCommandExecutionApi.SuccessFacts(scope, "RequirementAnalysis", response.ref().entity().entityId().toString(),
                        actor.correlationId(), JsonUtils.toJsonString(response), null, null));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PLATFORM_COMMAND_KEY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PLATFORM_COMMAND_IN_PROGRESS);
        return result.response();
    }

    // Required by the existing command API to distinguish retries from different requests using the same key.
    private String digest(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Create(Long projectId, ProjectBusinessExecutionSelection execution) {}
    public record Patch(Map<String, Object> values, Long extensionDefinitionRevisionId, int expectedExtensionVersion,
                        Map<String, Object> extensionValues, ProjectBusinessExecutionSelection execution) {}
    public record Action(String reason, ProjectBusinessExecutionSelection execution) {}
}
