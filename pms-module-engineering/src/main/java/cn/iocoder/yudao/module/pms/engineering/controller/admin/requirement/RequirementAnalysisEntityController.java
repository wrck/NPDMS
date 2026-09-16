package cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/requirement-analyses")
@RequiredArgsConstructor
public class RequirementAnalysisEntityController {
    private final RequirementAnalysisEntityQueryService queries;
    private final RequirementAnalysisEntityCommands commands;
    private final EntityVersionApi versions;

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:query')")
    public CommonResult<RequirementAnalysisEntityQueryService.Workspace> workspace(@RequestParam Long projectId,
            @RequestParam(required = false) Long stageId, @RequestParam(required = false) Long taskId) {
        return success(queries.workspace(projectId, actor(), stageId, taskId));
    }

    @GetMapping("/revisions/{revisionId}")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:query')")
    public CommonResult<RequirementAnalysisEntityQueryService.View> revision(@PathVariable Long revisionId) {
        return success(queries.revision(revisionId, actor()));
    }

    @GetMapping("/{entityId}/revisions")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:query')")
    public CommonResult<List<EntityVersionProvider.Revision>> revisions(@PathVariable Long entityId,
            @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "20") int limit) {
        return success(versions.history(entity(entityId), actor(), beforeId, limit));
    }

    @GetMapping("/{entityId}/compare")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:query')")
    public CommonResult<List<EntityVersionApi.FieldDifference>> compare(@PathVariable Long entityId,
            @RequestParam Long leftRevisionId, @RequestParam Long rightRevisionId) {
        var left = new RevisionRef(entity(entityId), leftRevisionId);
        var right = new RevisionRef(entity(entityId), rightRevisionId);
        var differences = new java.util.ArrayList<>(versions.compare(left, right, actor()));
        differences.addAll(queries.attachmentDifferences(left, right, actor()));
        return success(differences);
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:manage')")
    public CommonResult<EntityVersionProvider.Revision> create(@RequestHeader("Idempotency-Key") String key,
            @RequestBody RequirementAnalysisEntityCommands.Create request) {
        return success(commands.create(request, actor(), key));
    }

    @PatchMapping("/{entityId}/revisions/{revisionId}")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:manage')")
    public CommonResult<EntityVersionProvider.Revision> save(@PathVariable Long entityId, @PathVariable Long revisionId,
            @RequestHeader("If-Match") int expectedVersion, @RequestHeader("Idempotency-Key") String key,
            @RequestBody RequirementAnalysisEntityCommands.Patch request) {
        return success(commands.save(new RevisionRef(entity(entityId), revisionId), expectedVersion, request, actor(), key));
    }

    @PostMapping("/{entityId}/revisions/{revisionId}/complete")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:manage')")
    public CommonResult<EntityVersionProvider.Revision> complete(@PathVariable Long entityId, @PathVariable Long revisionId,
            @RequestHeader("If-Match") int expectedVersion, @RequestHeader("Idempotency-Key") String key,
            @RequestBody RequirementAnalysisEntityCommands.Action request) {
        return success(commands.complete(new RevisionRef(entity(entityId), revisionId), expectedVersion, request, actor(), key));
    }

    @PostMapping("/{entityId}/revisions/{revisionId}/copy")
    @PreAuthorize("@ss.hasPermission('pms:requirement-analysis:manage')")
    public CommonResult<EntityVersionProvider.Revision> copy(@PathVariable Long entityId, @PathVariable Long revisionId,
            @RequestHeader("If-Match") int expectedVersion, @RequestHeader("Idempotency-Key") String key,
            @RequestBody RequirementAnalysisEntityCommands.Action request) {
        return success(commands.copy(new RevisionRef(entity(entityId), revisionId), expectedVersion, request, actor(), key));
    }

    private EntityActor actor() { return new EntityActor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), java.util.UUID.randomUUID().toString()); }
    private EntityRef entity(Long id) { return new EntityRef(TenantContextHolder.getRequiredTenantId(), "SOL", "REQUIREMENT_ANALYSIS", id); }
}
