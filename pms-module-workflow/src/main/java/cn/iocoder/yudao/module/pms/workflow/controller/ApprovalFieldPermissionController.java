package cn.iocoder.yudao.module.pms.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.iocoder.yudao.module.pms.workflow.annotation.OperLog;
import cn.iocoder.yudao.module.pms.workflow.exception.BusinessException;
import cn.iocoder.yudao.module.pms.workflow.common.Result;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalFieldPermission;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalFieldPermissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批字段权限配置控制器（Story 6）。
 *
 * <p>挂载在 {@code /api/workflow/field-perm} 下，提供 4 个端点：list / save / update / delete。
 * 用于配置审批节点对业务字段的可见性（VISIBLE / MASKED / HIDDEN）与脱敏规则。</p>
 *
 * <p>关联设计文档：§2.2 ApprovalFieldPermission（行 233-243）、§5.7。</p>
 *
 * <p>权限码：{@code workflow:field:perm}（字段权限管理）。采用 Spring Security
 * {@code @PreAuthorize}。</p>
 */
@Slf4j
@Tag(name = "审批字段权限配置", description = "Approval Field Permission Config (Story 6)")
@RestController
@RequestMapping("/api/workflow/field-perm")
@RequiredArgsConstructor
public class ApprovalFieldPermissionController {

    private final ApprovalFieldPermissionMapper fieldPermissionMapper;

    @Operation(summary = "查询字段权限列表（按节点 + 实体类型过滤）")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('workflow:field:perm')")
    public Result<List<ApprovalFieldPermission>> list(
            @RequestParam Long approvalNodeId,
            @RequestParam(required = false) String entityType) {
        LambdaQueryWrapper<ApprovalFieldPermission> wrapper = new LambdaQueryWrapper<ApprovalFieldPermission>()
                .eq(ApprovalFieldPermission::getApprovalNodeId, approvalNodeId);
        if (entityType != null && !entityType.isBlank()) {
            wrapper.eq(ApprovalFieldPermission::getEntityType, entityType);
        }
        wrapper.orderByAsc(ApprovalFieldPermission::getEntityType)
                .orderByAsc(ApprovalFieldPermission::getFieldName);
        return Result.ok(fieldPermissionMapper.selectList(wrapper));
    }

    @Operation(summary = "新增字段权限")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('workflow:field:perm')")
    @OperLog(title = "字段权限-新增", businessType = 1)
    public Result<ApprovalFieldPermission> save(@RequestBody ApprovalFieldPermission permission) {
        validatePermission(permission);
        if (permission.getPermission() == null || permission.getPermission().isBlank()) {
            permission.setPermission("VISIBLE");
        }
        fieldPermissionMapper.insert(permission);
        log.info("字段权限已新增：id={}, nodeId={}, field={}",
                permission.getId(), permission.getApprovalNodeId(), permission.getFieldName());
        return Result.ok(permission);
    }

    @Operation(summary = "更新字段权限")
    @PutMapping
    @PreAuthorize("@ss.hasPermission('workflow:field:perm')")
    @OperLog(title = "字段权限-更新", businessType = 2)
    public Result<ApprovalFieldPermission> update(@RequestBody ApprovalFieldPermission permission) {
        if (permission.getId() == null) {
            throw new BusinessException("字段权限ID不能为空");
        }
        validatePermission(permission);
        fieldPermissionMapper.updateById(permission);
        log.info("字段权限已更新：id={}", permission.getId());
        return Result.ok(permission);
    }

    @Operation(summary = "删除字段权限")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('workflow:field:perm')")
    @OperLog(title = "字段权限-删除", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        fieldPermissionMapper.deleteById(id);
        log.info("字段权限已删除：id={}", id);
        return Result.ok();
    }

    // ===================== 内部辅助 =====================

    private void validatePermission(ApprovalFieldPermission permission) {
        if (permission.getApprovalNodeId() == null) {
            throw new BusinessException("审批节点ID不能为空");
        }
        if (permission.getEntityType() == null || permission.getEntityType().isBlank()) {
            throw new BusinessException("业务实体类型不能为空");
        }
        if (permission.getFieldName() == null || permission.getFieldName().isBlank()) {
            throw new BusinessException("字段名不能为空");
        }
        String perm = permission.getPermission();
        if (perm != null && !perm.isBlank()
                && !"VISIBLE".equalsIgnoreCase(perm)
                && !"MASKED".equalsIgnoreCase(perm)
                && !"HIDDEN".equalsIgnoreCase(perm)) {
            throw new BusinessException("权限值非法，必须为 VISIBLE / MASKED / HIDDEN");
        }
        // MASKED 必须配置 maskPattern
        if ("MASKED".equalsIgnoreCase(perm)
                && (permission.getMaskPattern() == null || permission.getMaskPattern().isBlank())) {
            throw new BusinessException("MASKED 权限必须配置脱敏规则 maskPattern");
        }
        // custom 规则必须配置 customPattern 正则
        if ("custom".equalsIgnoreCase(permission.getMaskPattern())
                && (permission.getCustomPattern() == null || permission.getCustomPattern().isBlank())) {
            throw new BusinessException("custom 脱敏规则必须配置 customPattern 正则");
        }
    }
}
