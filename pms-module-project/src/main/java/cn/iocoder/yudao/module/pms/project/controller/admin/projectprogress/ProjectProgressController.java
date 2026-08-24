package cn.iocoder.yudao.module.pms.project.controller.admin.projectprogress;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectprogress.vo.ProjectProgressPolicyReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectprogress.vo.ProjectProgressPolicyRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectprogress.vo.ProjectProgressRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyRevisionDO;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.ProjectProgressPolicyService;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.ProjectProgressQueryService;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.CreateProgressPolicyCommand;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.ProjectProgressResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 项目进度策略与汇总")
@RestController
@RequestMapping("/pms")
@RequiredArgsConstructor
public class ProjectProgressController {
    private final ProjectProgressPolicyService policyService;
    private final ProjectProgressQueryService queryService;

    @PostMapping("/projects/{projectId}/progress-policies")
    @Operation(summary = "创建项目进度策略修订")
    @PreAuthorize("@ss.hasPermission('pms:project:progress-policy:update')")
    public CommonResult<Long> createPolicy(@PathVariable Long projectId,
                                           @Valid @RequestBody ProjectProgressPolicyReqVO request) {
        List<CreateProgressPolicyCommand.Item> items = request.getItems() == null ? List.of()
                : request.getItems().stream().map(item -> new CreateProgressPolicyCommand.Item(
                        item.getChildProjectId(), item.getWeight(), item.getIncludeStatuses())).toList();
        return success(policyService.createRevision(new CreateProgressPolicyCommand(
                projectId, request.getPolicyType(), items), actor()));
    }

    @PostMapping("/progress-policies/{revisionId}/actions/submit")
    @Operation(summary = "提交项目进度策略审批")
    @PreAuthorize("@ss.hasPermission('pms:project:progress-policy:submit')")
    public CommonResult<String> submitPolicy(@PathVariable Long revisionId,
                                             @RequestHeader("If-Match") Integer expectedVersion) {
        return success(policyService.submitForApproval(revisionId, expectedVersion, actor()));
    }

    @GetMapping("/projects/{projectId}/progress-policies")
    @Operation(summary = "查询项目进度策略历史")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<List<ProjectProgressPolicyRespVO>> listPolicies(@PathVariable Long projectId) {
        List<ProjectProgressPolicyRevisionDO> revisions = policyService.listByParent(projectId, actor());
        Map<Long, List<ProjectProgressPolicyItemDO>> items = policyService.listItemsByRevisionIds(
                revisions.stream().map(ProjectProgressPolicyRevisionDO::getId).toList());
        return success(revisions.stream().map(revision -> toPolicyVO(
                revision, items.getOrDefault(revision.getId(), List.of()))).toList());
    }

    @GetMapping("/projects/{projectId}/progress")
    @Operation(summary = "查询项目当前进度汇总")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectProgressRespVO> getProgress(@PathVariable Long projectId) {
        return success(toProgressVO(queryService.getCurrent(projectId, actor())));
    }

    private ProjectProgressPolicyRespVO toPolicyVO(ProjectProgressPolicyRevisionDO revision,
                                                   List<ProjectProgressPolicyItemDO> items) {
        ProjectProgressPolicyRespVO result = new ProjectProgressPolicyRespVO();
        result.setId(revision.getId());
        result.setParentProjectId(revision.getParentProjectId());
        result.setRevisionNo(revision.getRevisionNo());
        result.setStatus(revision.getStatus());
        result.setPolicyType(revision.getPolicyType());
        result.setProcessInstanceId(revision.getProcessInstanceId());
        result.setEffectiveFrom(revision.getEffectiveFrom());
        result.setEffectiveTo(revision.getEffectiveTo());
        result.setApprovedBy(revision.getApprovedBy());
        result.setApprovedAt(revision.getApprovedAt());
        result.setVersion(revision.getVersion());
        result.setItems(items.stream().map(this::toPolicyItemVO).toList());
        return result;
    }

    private ProjectProgressPolicyRespVO.Item toPolicyItemVO(ProjectProgressPolicyItemDO item) {
        ProjectProgressPolicyRespVO.Item result = new ProjectProgressPolicyRespVO.Item();
        result.setChildProjectId(item.getChildProjectId());
        result.setWeight(item.getWeight());
        result.setIncludeStatuses(JsonUtils.parseArray(item.getIncludeStatusSnapshot(), String.class));
        return result;
    }

    private ProjectProgressRespVO toProgressVO(ProjectProgressResult progress) {
        ProjectProgressRespVO result = new ProjectProgressRespVO();
        result.setProjectId(progress.projectId());
        result.setPolicyRevisionId(progress.policyRevisionId());
        result.setTreeVersion(progress.treeVersion());
        result.setSourceWatermark(progress.sourceWatermark());
        result.setStatus(progress.status());
        result.setProgress(progress.progress());
        result.setItems(progress.items().stream().map(item -> {
            ProjectProgressRespVO.Item value = new ProjectProgressRespVO.Item();
            value.setChildProjectId(item.childProjectId());
            value.setFactVersion(item.factVersion());
            value.setChildProgress(item.childProgress());
            value.setNormalizedWeight(item.normalizedWeight());
            value.setContribution(item.contribution());
            value.setMissingReason(item.missingReason());
            return value;
        }).toList());
        return result;
    }

    private ProjectProgressPolicyService.Actor actor() {
        return new ProjectProgressPolicyService.Actor(currentTenantId(),
                SecurityFrameworkUtils.getLoginUserId(), UUID.randomUUID().toString());
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? tenantId : 0L;
    }
}
