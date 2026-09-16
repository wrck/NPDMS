package cn.iocoder.yudao.module.pms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.iocoder.yudao.module.pms.workflow.security.SecurityUtils;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalFieldPermission;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalHistory;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalNode;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalFieldPermissionMapper;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.workflow.service.ApprovalCenterService;
import cn.iocoder.yudao.module.pms.platform.api.spi.BusinessDataLoader;
import cn.iocoder.yudao.module.pms.workflow.service.SensitiveFieldMasker;
import cn.iocoder.yudao.module.pms.workflow.vo.ApprovalDetailVO;
import cn.iocoder.yudao.module.pms.workflow.vo.ApprovalStatisticsVO;
import cn.iocoder.yudao.module.pms.workflow.vo.MaskedFieldVO;
import cn.iocoder.yudao.module.pms.workflow.annotation.OperLog;
import cn.iocoder.yudao.module.pms.workflow.exception.BusinessException;
import cn.iocoder.yudao.module.pms.workflow.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一审批中心控制器（Story 6）。
 *
 * <p>挂载在 {@code /api/workflow/approval} 下，提供 11 个端点：待办/已提交/项目维度/详情/
 * 历史/通过/退回/撤回/重新提交/统计/通用列表。</p>
 *
 * <p>关联设计文档：§5.7 统一审批中心 API（行 1080-1147）。</p>
 *
 * <p>权限码：{@code workflow:approval:handle}（待办/详情/通过/退回）。撤回/重新提交/已提交
 * /项目维度/历史/统计为无严格权限（提交人本人或只读）。采用 Spring Security
 * {@code @PreAuthorize}（与 pms-baseline 模块一致）。</p>
 */
@Slf4j
@Tag(name = "统一审批中心", description = "Unified Approval Center APIs (Story 6)")
@RestController
@RequestMapping("/api/workflow/approval")
@RequiredArgsConstructor
public class ApprovalCenterController {

    private final ApprovalCenterService approvalCenterService;
    private final ApprovalFieldPermissionMapper fieldPermissionMapper;
    private final ApprovalNodeMapper approvalNodeMapper;
    private final SensitiveFieldMasker sensitiveFieldMasker;
    /** 业务数据加载器列表（按审批类型路由），可为空列表。 */
    private final List<BusinessDataLoader> businessDataLoaders;

    // ===================== 列表查询（只读） =====================

    @Operation(summary = "我的待办审批")
    @GetMapping("/pending")
    @PreAuthorize("@ss.hasPermission('workflow:approval:handle')")
    public Result<List<ApprovalRecord>> pending() {
        Long userId = currentUserId();
        return Result.ok(approvalCenterService.listPending(userId));
    }

    @Operation(summary = "我提交的审批")
    @GetMapping("/submitted")
    public Result<List<ApprovalRecord>> submitted() {
        Long userId = currentUserId();
        return Result.ok(approvalCenterService.listSubmitted(userId));
    }

    @Operation(summary = "项目维度审批列表")
    @GetMapping("/project/{projectId}")
    public Result<List<ApprovalRecord>> listByProject(@PathVariable Long projectId) {
        return Result.ok(approvalCenterService.listByProject(projectId));
    }

    @Operation(summary = "通用审批列表（可按状态/类型/项目过滤）")
    @GetMapping("/list")
    public Result<List<ApprovalRecord>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalType,
            @RequestParam(required = false) Long projectId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApprovalRecord> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(ApprovalRecord::getStatus, status);
        }
        if (approvalType != null && !approvalType.isBlank()) {
            wrapper.eq(ApprovalRecord::getApprovalType, approvalType);
        }
        if (projectId != null) {
            wrapper.eq(ApprovalRecord::getProjectId, projectId);
        }
        wrapper.orderByDesc(ApprovalRecord::getSubmittedAt);
        return Result.ok(approvalCenterService.list(wrapper));
    }

    @Operation(summary = "审批统计（按状态聚合）")
    @GetMapping("/statistics")
    public Result<ApprovalStatisticsVO> statistics() {
        Long userId = currentUserId();
        return Result.ok(approvalCenterService.statistics(userId));
    }

    // ===================== 详情与历史 =====================

    @Operation(summary = "审批详情（含字段脱敏）")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('workflow:approval:handle')")
    public Result<ApprovalDetailVO> detail(@PathVariable Long id) {
        ApprovalRecord record = approvalCenterService.getById(id);
        if (record == null) {
            throw new BusinessException("审批记录不存在：id=" + id);
        }

        // 1. 加载当前节点的字段权限
        List<ApprovalFieldPermission> perms = Collections.emptyList();
        ApprovalNode currentNode = findCurrentPendingNode(record.getId());
        Long nodeId = currentNode != null ? currentNode.getId() : null;
        if (nodeId != null) {
            perms = fieldPermissionMapper.selectList(
                    new LambdaQueryWrapper<ApprovalFieldPermission>()
                            .eq(ApprovalFieldPermission::getApprovalNodeId, nodeId));
        }

        // 2. 加载业务数据（按审批类型路由加载器）
        Map<String, Object> businessData = loadBusinessData(record.getApprovalType(), record.getBusinessId());

        // 3. 脱敏业务数据
        Map<String, Object> maskedData = sensitiveFieldMasker.maskMap(businessData, perms);

        // 4. 构建脱敏字段元数据（TD-P8-013 修复：复用 maskedData 中已脱敏的值）
        List<MaskedFieldVO> maskedFields = buildMaskedFields(maskedData, perms);

        // 5. 加载历史
        List<ApprovalHistory> history = approvalCenterService.listHistory(id);

        ApprovalDetailVO vo = ApprovalDetailVO.builder()
                .record(record)
                .businessData(maskedData)
                .maskedFields(maskedFields)
                .history(history)
                .build();
        return Result.ok(vo);
    }

    @Operation(summary = "审批历史（含所有轮次）")
    @GetMapping("/{id}/history")
    public Result<List<ApprovalHistory>> history(@PathVariable Long id) {
        return Result.ok(approvalCenterService.listHistory(id));
    }

    // ===================== 流转操作 =====================

    @Operation(summary = "通过当前节点")
    @PostMapping("/{id}/approve")
    @PreAuthorize("@ss.hasPermission('workflow:approval:handle')")
    @OperLog(title = "审批-通过", businessType = 1)
    public Result<ApprovalRecord> approve(@PathVariable Long id,
                                          @RequestParam(required = false) String comment) {
        Long operatorId = currentUserId();
        Long nodeId = resolveCurrentNodeForUser(id, operatorId);
        return Result.ok(approvalCenterService.approve(nodeId, comment, operatorId));
    }

    @Operation(summary = "退回当前节点")
    @PostMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermission('workflow:approval:handle')")
    @OperLog(title = "审批-退回", businessType = 2)
    public Result<ApprovalRecord> reject(@PathVariable Long id,
                                         @RequestParam(required = false) String comment) {
        Long operatorId = currentUserId();
        Long nodeId = resolveCurrentNodeForUser(id, operatorId);
        return Result.ok(approvalCenterService.reject(nodeId, comment, operatorId));
    }

    @Operation(summary = "撤回审批（仅提交人）")
    @PostMapping("/{id}/withdraw")
    @OperLog(title = "审批-撤回", businessType = 2)
    public Result<ApprovalRecord> withdraw(@PathVariable Long id) {
        Long operatorId = currentUserId();
        return Result.ok(approvalCenterService.withdraw(id, operatorId));
    }

    @Operation(summary = "重新提交（保留历史，round+1）")
    @PostMapping("/{id}/resubmit")
    @OperLog(title = "审批-重新提交", businessType = 1)
    public Result<ApprovalRecord> resubmit(@PathVariable Long id,
                                            @RequestParam(required = false) String comment) {
        return Result.ok(approvalCenterService.resubmit(id, comment));
    }

    // ===================== 内部辅助 =====================

    private Long currentUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("无法获取当前用户ID，请先登录");
        }
        return uid;
    }

    /** 查找审批记录的当前 PENDING 节点。 */
    private ApprovalNode findCurrentPendingNode(Long recordId) {
        return approvalNodeMapper.selectOne(
                new LambdaQueryWrapper<ApprovalNode>()
                        .eq(ApprovalNode::getRecordId, recordId)
                        .eq(ApprovalNode::getStatus, "PENDING")
                        .orderByAsc(ApprovalNode::getNodeOrder)
                        .last("LIMIT 1"));
    }

    /** 解析当前用户在审批记录上的待办节点。 */
    private Long resolveCurrentNodeForUser(Long recordId, Long userId) {
        ApprovalNode node = approvalNodeMapper.selectOne(
                new LambdaQueryWrapper<ApprovalNode>()
                        .eq(ApprovalNode::getRecordId, recordId)
                        .eq(ApprovalNode::getStatus, "PENDING")
                        .eq(ApprovalNode::getApproverId, userId)
                        .last("LIMIT 1"));
        if (node == null) {
            throw new BusinessException("未找到当前用户的待办审批节点：recordId=" + recordId);
        }
        return node.getId();
    }

    /** 按审批类型路由业务数据加载器。 */
    private Map<String, Object> loadBusinessData(String approvalType, Long businessId) {
        if (approvalType == null || businessId == null) {
            return new LinkedHashMap<>();
        }
        if (businessDataLoaders != null) {
            for (BusinessDataLoader loader : businessDataLoaders) {
                if (approvalType.equals(loader.supportedType())) {
                    return loader.load(approvalType, businessId);
                }
            }
        }
        return new LinkedHashMap<>();
    }

    /**
     * 构建脱敏字段元数据列表（仅 MASKED 字段）。
     *
     * <p>TD-P8-013 修复：参数由原始 {@code businessData} 改为已脱敏的 {@code maskedData}，
     * 避免对同一批 MASKED 字段重复调用 {@code sensitiveFieldMasker.mask}（maskMap 已脱敏）。</p>
     *
     * <p>TD-P8-014 修复：跳过 HIDDEN 字段。HIDDEN 字段已被 {@code maskMap} 从
     * {@code maskedData} 中移除，前端不会渲染，故 {@code maskedFields} 中无需为
     * HIDDEN 字段生成冗余元数据项。</p>
     */
    private List<MaskedFieldVO> buildMaskedFields(Map<String, Object> maskedData,
                                                   List<ApprovalFieldPermission> perms) {
        List<MaskedFieldVO> result = new ArrayList<>();
        if (perms == null) {
            return result;
        }
        for (ApprovalFieldPermission perm : perms) {
            if (perm.getFieldName() == null) {
                continue;
            }
            // TD-P8-014：跳过 HIDDEN 字段，不生成元数据项（前端无消费者）
            if ("HIDDEN".equalsIgnoreCase(perm.getPermission())) {
                continue;
            }
            if ("MASKED".equalsIgnoreCase(perm.getPermission())) {
                // TD-P8-013：直接复用 maskedData 中已脱敏的值，避免重复脱敏计算
                Object masked = maskedData == null ? null : maskedData.get(perm.getFieldName());
                result.add(MaskedFieldVO.builder()
                        .fieldName(perm.getFieldName())
                        .permission("MASKED")
                        .maskedValue(masked == null ? null : String.valueOf(masked))
                        .maskPattern(perm.getMaskPattern())
                        .build());
            }
        }
        return result;
    }
}
