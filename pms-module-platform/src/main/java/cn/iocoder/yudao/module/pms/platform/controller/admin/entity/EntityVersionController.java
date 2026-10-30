package cn.iocoder.yudao.module.pms.platform.controller.admin.entity;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.RevisionRef;
import cn.iocoder.yudao.module.pms.platform.service.entity.EntityVersionService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 公共内容历史端点：面向声明了继承式内容历史（或自带版本 Provider）的实体，
 * 提供修订历史、发起修订、保存修订草稿与冻结生效；修订内容读取复用统一数据端点（revisionId 参数）。
 */
@RestController
@RequestMapping("/api/v1/pms/entities")
@Validated
public class EntityVersionController {

    private final EntityVersionService versionService;

    public EntityVersionController(EntityVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/{ownerModule}/{entityType}/{entityId}/revisions")
    @PreAuthorize("@ss.hasPermission('pms:business-model:query')")
    public CommonResult<List<EntityVersionProvider.Revision>> history(@PathVariable String ownerModule,
                                                                      @PathVariable String entityType,
                                                                      @PathVariable @Positive Long entityId,
                                                                      @RequestParam(required = false) @Positive Long beforeId,
                                                                      @RequestParam(defaultValue = "20") int limit) {
        EntityRef ref = new EntityRef(TenantContextHolder.getRequiredTenantId(), ownerModule, entityType, entityId);
        return success(versionService.history(ref, actor(), beforeId, limit));
    }

    @PostMapping("/{ownerModule}/{entityType}/{entityId}/revisions")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<EntityVersionProvider.Revision> create(@PathVariable String ownerModule,
                                                               @PathVariable String entityType,
                                                               @PathVariable @Positive Long entityId,
                                                               @Valid @RequestBody CreateReqVO request) {
        EntityRef ref = new EntityRef(TenantContextHolder.getRequiredTenantId(), ownerModule, entityType, entityId);
        RevisionRef source = request.getSourceRevisionId() == null ? null
                : new RevisionRef(ref, request.getSourceRevisionId());
        return success(versionService.create(ref, source, request.getReason(), actor()));
    }

    @PostMapping("/{ownerModule}/{entityType}/{entityId}/revisions/{revisionId}/save")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<EntityVersionProvider.Revision> save(@PathVariable String ownerModule,
                                                             @PathVariable String entityType,
                                                             @PathVariable @Positive Long entityId,
                                                             @PathVariable @Positive Long revisionId,
                                                             @Valid @RequestBody SaveReqVO request) {
        return success(versionService.save(
                new RevisionRef(new EntityRef(TenantContextHolder.getRequiredTenantId(), ownerModule,
                        entityType, entityId), revisionId),
                request.getExpectedVersion(), request.getFields() == null ? Map.of() : request.getFields(),
                actor()));
    }

    @PostMapping("/{ownerModule}/{entityType}/{entityId}/revisions/{revisionId}/complete")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<EntityVersionProvider.Revision> complete(@PathVariable String ownerModule,
                                                                 @PathVariable String entityType,
                                                                 @PathVariable @Positive Long entityId,
                                                                 @PathVariable @Positive Long revisionId,
                                                                 @Valid @RequestBody CompleteReqVO request) {
        return success(versionService.complete(
                new RevisionRef(new EntityRef(TenantContextHolder.getRequiredTenantId(), ownerModule,
                        entityType, entityId), revisionId),
                request.getExpectedVersion(), actor()));
    }

    @PostMapping("/{ownerModule}/{entityType}/{entityId}/revisions/{revisionId}/discard")
    @PreAuthorize("@ss.hasPermission('pms:business-model:operate')")
    public CommonResult<Boolean> discard(@PathVariable String ownerModule,
                                         @PathVariable String entityType,
                                         @PathVariable @Positive Long entityId,
                                         @PathVariable @Positive Long revisionId) {
        versionService.discard(
                new RevisionRef(new EntityRef(TenantContextHolder.getRequiredTenantId(), ownerModule,
                        entityType, entityId), revisionId),
                actor());
        return success(true);
    }

    private EntityActor actor() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                    "CALLER_UNRESOLVED", "缺少可信操作者身份");
        }
        return new EntityActor(TenantContextHolder.getRequiredTenantId(), userId, null);
    }

    public static class CreateReqVO {
        @Positive
        private Long sourceRevisionId;
        @NotBlank
        private String reason;

        public Long getSourceRevisionId() {
            return sourceRevisionId;
        }

        public void setSourceRevisionId(Long sourceRevisionId) {
            this.sourceRevisionId = sourceRevisionId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class SaveReqVO {
        @NotNull
        private Integer expectedVersion;
        private Map<String, Object> fields;

        public Integer getExpectedVersion() {
            return expectedVersion;
        }

        public void setExpectedVersion(Integer expectedVersion) {
            this.expectedVersion = expectedVersion;
        }

        public Map<String, Object> getFields() {
            return fields;
        }

        public void setFields(Map<String, Object> fields) {
            this.fields = fields;
        }
    }

    public static class CompleteReqVO {
        @NotNull
        private Integer expectedVersion;

        public Integer getExpectedVersion() {
            return expectedVersion;
        }

        public void setExpectedVersion(Integer expectedVersion) {
            this.expectedVersion = expectedVersion;
        }
    }
}
