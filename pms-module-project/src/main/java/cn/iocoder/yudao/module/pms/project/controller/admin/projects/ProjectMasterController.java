package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectAssignManagerReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectAssignManagerRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectChildWeightsReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectInstancesRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMatchTemplatesRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMemberAssignmentRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectProgressRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectTreeMoveReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectUpdateReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManagerAssignmentApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManagerAssignmentApplicationService.Actor;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectTreeService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_WEIGHT_SUM_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ASSIGNMENT_REQUEST_INVALID;

/**
 * 管理后台 - PMS 项目手工创建 Controller（F-PM01 / PM-01）。
 * <p>
 * 新链复数路由 {@code /pms/projects}（SDS 10 §5 契约），承接旧语义权限码
 * {@code pms:project:query/create/update/assign}（V57 菜单 18067~18070）。
 * 创建端点支持 {@code Idempotency-Key} 幂等：作用域 tenant+command+actor，
 * 同键同摘要重放返回原资源，同键异摘要 409（PMS-COMMON-IDEMPOTENCY-0001）。
 */
@Tag(name = "管理后台 - PMS 项目")
@RestController
@RequestMapping("/pms/projects")
@Validated
public class ProjectMasterController {

    @Resource
    private ProjectManualCreationService projectManualCreationService;
    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private ProjectManualCreationApplicationService projectManualCreationApplicationService;
    @Resource
    private ProjectManagerAssignmentApplicationService projectManagerAssignmentApplicationService;
    @Resource
    private ProjectTreeService projectTreeService;

    @PostMapping
    @Operation(summary = "手工创建项目（Idempotency-Key 幂等；单事务创建+实例化+可选指派）")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ProjectCreateRespVO> createProject(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ProjectCreateReqVO createReqVO) {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        ProjectMasterDO draft = BeanUtils.toBean(createReqVO, ProjectMasterDO.class);
        ManualProjectCreateCommand command = new ManualProjectCreateCommand(
                draft, createReqVO.getOrderOfficeCompanyCode(), createReqVO.getOrderOfficeDepartmentCode(),
                createReqVO.getTemplateRevisionId(), createReqVO.getCandidateWatermark(),
                createReqVO.getServiceManagerUserId(), idempotencyKey,
                sha256Digest(JsonUtils.toJsonString(createReqVO)));
        ManualProjectCreateResult result = projectManualCreationApplicationService.create(command,
                new ProjectManualCreationApplicationService.Actor(
                        currentTenantId(), actorId, UUID.randomUUID().toString()));
        return success(toResponse(result));
    }

    @GetMapping("/actions/match-templates")
    @Operation(summary = "按三维+级别返回命中生效模板列表（含版本概要，供创建向导选择）")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ProjectMatchTemplatesRespVO> matchTemplates(
            @RequestParam(value = "signingMethod", required = false) String signingMethod,
            @RequestParam(value = "projectCategory", required = false) String projectCategory,
            @RequestParam(value = "implementationMode", required = false) String implementationMode,
            @RequestParam(value = "majorProjectLevel", required = false) String majorProjectLevel) {
        TemplateMatchResult match = projectTemplateService.matchPreview(
                signingMethod, projectCategory, implementationMode, majorProjectLevel);
        ProjectMatchTemplatesRespVO respVO = new ProjectMatchTemplatesRespVO();
        respVO.setOutcome(match.getOutcome().name());
        respVO.setCandidateWatermark(match.getCandidateWatermark());
        respVO.setConflicts(match.getConflicts());
        respVO.setCandidates(BeanUtils.toBean(match.getCandidates(), ProjectMatchTemplatesRespVO.CandidateItem.class));
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询项目（名称/编码/状态/三维过滤）")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<PageResult<ProjectRespVO>> getProjectPage(@Valid ProjectPageReqVO pageReqVO) {
        PageResult<ProjectMasterDO> pageResult = projectManualCreationService.getProjectPage(
                pageReqVO, pageReqVO.getProjectName(), pageReqVO.getProjectCode(), pageReqVO.getStatus(),
                pageReqVO.getSigningMethod(), pageReqVO.getProjectCategory(), pageReqVO.getImplementationMode());
        return success(BeanUtils.toBean(pageResult, ProjectRespVO.class));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询项目详情（基本信息+四维+模板绑定）")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectRespVO> getProject(@PathVariable("id") Long id) {
        ProjectMasterDO project = projectManualCreationService.getProject(id);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return success(BeanUtils.toBean(project, ProjectRespVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新可编辑属性（BR-7：编码/父节点/来源/模板绑定/状态不可改）")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> updateProject(@PathVariable("id") Long id,
                                               @Valid @RequestBody ProjectUpdateReqVO updateReqVO) {
        ProjectMasterDO update = BeanUtils.toBean(updateReqVO, ProjectMasterDO.class);
        update.setId(id);
        projectManualCreationService.updateProject(update);
        return success(true);
    }

    @GetMapping("/{id}/instances")
    @Operation(summary = "实例视图（阶段→任务/里程碑/交付件/门禁+引用行，按冻结版本只读）")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectInstancesRespVO> getProjectInstances(@PathVariable("id") Long id) {
        ProjectMasterDO project = projectManualCreationService.getProject(id);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectInstantiation instantiation = projectManualCreationService.getInstances(id);
        ProjectInstancesRespVO respVO = new ProjectInstancesRespVO();
        respVO.setProjectId(id);
        respVO.setLifecycleTemplateId(project.getLifecycleTemplateId());
        respVO.setLifecycleTemplateRevisionNo(project.getLifecycleTemplateRevisionNo());
        respVO.setStages(BeanUtils.toBean(instantiation.getStages(), ProjectInstancesRespVO.StageItem.class));
        respVO.setTasks(BeanUtils.toBean(instantiation.getTasks(), ProjectInstancesRespVO.TaskItem.class));
        respVO.setMilestones(BeanUtils.toBean(instantiation.getMilestones(), ProjectInstancesRespVO.MilestoneItem.class));
        respVO.setDeliverables(BeanUtils.toBean(instantiation.getDeliverables(), ProjectInstancesRespVO.DeliverableItem.class));
        respVO.setGates(BeanUtils.toBean(instantiation.getGates(), ProjectInstancesRespVO.GateItem.class));
        // 门禁引用行按 gateCode 分组回填
        instantiation.getGates().forEach(gate -> {
            ProjectInstancesRespVO.GateItem gateItem = respVO.getGates().stream()
                    .filter(item -> gate.getGateCode() != null && gate.getGateCode().equals(item.getGateCode()))
                    .findFirst().orElse(null);
            if (gateItem != null && instantiation.getGateReferencesByGateCode().containsKey(gate.getGateCode())) {
                gateItem.setReferences(BeanUtils.toBean(
                        instantiation.getGateReferencesByGateCode().get(gate.getGateCode()),
                        ProjectInstancesRespVO.GateReferenceItem.class));
            }
        });
        return success(respVO);
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "成员区间列表（当前有效+历史）")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<java.util.List<ProjectMemberAssignmentRespVO>> getProjectMembers(@PathVariable("id") Long id) {
        return success(BeanUtils.toBean(projectManualCreationService.getMemberAssignments(id),
                ProjectMemberAssignmentRespVO.class));
    }

    @PostMapping("/{id}/actions/assign-manager")
    @Operation(summary = "指派一级服务经理（旧区间关闭+新区间开启，留痕前后值）")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:assign')")
    public CommonResult<ProjectAssignManagerRespVO> assignManager(
            @PathVariable("id") Long id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader("If-Match") @NotBlank String ifMatch,
            @Valid @RequestBody ProjectAssignManagerReqVO assignReqVO) {
        Integer expectedVersion = parseIfMatch(ifMatch);
        String requestDigest = sha256Digest(id + ":" + expectedVersion + ":"
                + JsonUtils.toJsonString(assignReqVO));
        AssignServiceManagerCommand command = new AssignServiceManagerCommand(
                id, expectedVersion, assignReqVO.getRoleCode(), assignReqVO.getLevelCode(),
                assignReqVO.getUserId(), assignReqVO.getOfficeId(), assignReqVO.getLocationId(),
                assignReqVO.getEffectiveFrom(), idempotencyKey, requestDigest);
        AssignServiceManagerResult result = projectManagerAssignmentApplicationService.assign(command,
                new Actor(currentTenantId(), SecurityFrameworkUtils.getLoginUserId(),
                        UUID.randomUUID().toString()));
        ProjectAssignManagerRespVO response = new ProjectAssignManagerRespVO();
        response.setProjectId(result.projectId());
        response.setAssignmentId(result.assignmentId());
        response.setVersion(result.version());
        response.setAssignmentStatus(result.assignmentStatus());
        return success(response);
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "直接下级项目（按需加载）")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<java.util.List<ProjectRespVO>> getChildren(@PathVariable("id") Long id) {
        return success(BeanUtils.toBean(projectTreeService.getChildren(id), ProjectRespVO.class));
    }

    @GetMapping("/{id}/descendants")
    @Operation(summary = "全部后代项目")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<java.util.List<ProjectRespVO>> getDescendants(@PathVariable("id") Long id) {
        return success(BeanUtils.toBean(projectTreeService.getDescendants(id), ProjectRespVO.class));
    }

    @GetMapping("/{id}/ancestors")
    @Operation(summary = "完整上级链（根→父）")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<java.util.List<ProjectRespVO>> getAncestors(@PathVariable("id") Long id) {
        return success(BeanUtils.toBean(projectTreeService.getAncestors(id), ProjectRespVO.class));
    }

    @GetMapping("/actions/by-business-level")
    @Operation(summary = "按业务层级标签查询项目")
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<java.util.List<ProjectRespVO>> getByBusinessLevel(
            @RequestParam("businessLevelCode") String businessLevelCode) {
        return success(BeanUtils.toBean(projectTreeService.getByBusinessLevel(businessLevelCode), ProjectRespVO.class));
    }

    @PostMapping("/{id}/actions/move")
    @Operation(summary = "子树移动（校验无环后重建子树缓存）")
    @Parameter(name = "id", description = "被移动的项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> moveSubtree(@PathVariable("id") Long id,
                                             @Valid @RequestBody ProjectTreeMoveReqVO moveReqVO) {
        projectTreeService.moveSubtree(id, moveReqVO.getNewParentId());
        return success(true);
    }

    @PutMapping("/{id}/child-weights")
    @Operation(summary = "整组设置直接子项目人工权重（完整覆盖且合计100%）")
    @Parameter(name = "id", description = "父项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:update')")
    public CommonResult<Boolean> updateChildWeights(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProjectChildWeightsReqVO reqVO) {
        Map<Long, BigDecimal> weights = reqVO.getChildren().stream()
                .collect(Collectors.toMap(
                        ProjectChildWeightsReqVO.Item::getProjectId,
                        ProjectChildWeightsReqVO.Item::getWeight,
                        (left, right) -> right));
        if (weights.size() != reqVO.getChildren().size()) {
            throw exception(PROJECT_WEIGHT_SUM_INVALID, "子项目编号不得重复");
        }
        projectTreeService.updateChildWeights(id, weights);
        return success(true);
    }

    @GetMapping("/{id}/progress")
    @Operation(summary = "进度汇总（直接子项目进度列表 + 汇总进度）")
    @Parameter(name = "id", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project:query')")
    public CommonResult<ProjectProgressRespVO> getProgress(@PathVariable("id") Long id) {
        ProjectTreeService.ProjectProgress progress = projectTreeService.getProgress(id);
        ProjectProgressRespVO respVO = new ProjectProgressRespVO();
        respVO.setAggregate(progress.aggregate());
        respVO.setChildren(progress.children().stream().map(child -> {
            ProjectProgressRespVO.ChildItem item = new ProjectProgressRespVO.ChildItem();
            item.setProjectId(child.projectId());
            item.setProjectCode(child.projectCode());
            item.setProjectName(child.projectName());
            item.setProgress(child.progress());
            item.setNormalizedWeight(child.normalizedWeight());
            item.setWeightSource(child.weightSource());
            return item;
        }).toList());
        return success(respVO);
    }

    private ProjectCreateRespVO toResponse(ManualProjectCreateResult result) {
        ProjectCreateRespVO response = new ProjectCreateRespVO();
        response.setId(result.id());
        response.setProjectCode(result.projectCode());
        response.setStatus(result.status());
        response.setLifecycleStatus(result.lifecycleStatus());
        response.setCurrentStage(result.currentStage());
        response.setAssignmentStatus(result.assignmentStatus());
        response.setVersion(result.version());
        response.setLifecycleTemplateId(result.lifecycleTemplateId());
        response.setLifecycleTemplateRevisionNo(result.lifecycleTemplateRevisionNo());
        response.setTemplateLoadMethod(result.templateLoadMethod());
        response.setStageCount(result.stageCount());
        response.setTaskCount(result.taskCount());
        response.setMilestoneCount(result.milestoneCount());
        response.setDeliverableCount(result.deliverableCount());
        response.setGateCount(result.gateCount());
        response.setServiceManagerAssigned(result.serviceManagerAssigned());
        return response;
    }

    /**
     * 当前租户号：多租户取上下文；单租户（V1 enable=false 无上下文）回退 0，
     * 与各表 tenant_id DDL 默认值一致。
     */
    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? tenantId : 0L;
    }

    private String sha256Digest(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", ex);
        }
    }

    private Integer parseIfMatch(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            int version = Integer.parseInt(normalized);
            if (version < 0) {
                throw new NumberFormatException("negative version");
            }
            return version;
        } catch (NumberFormatException ex) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "If-Match必须是非负Project版本");
        }
    }
}
