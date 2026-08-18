package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectAssignManagerReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectInstancesRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMatchTemplatesRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMemberAssignmentRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectUpdateReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.IdempotencyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.IdempotencyRecordService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;

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
@Slf4j
public class ProjectMasterController {

    /** 幂等命令标识 */
    private static final String COMMAND_PROJECT_CREATE = "ProjectCreate";
    /** 幂等记录状态：已完成 */
    private static final String IDEMPOTENCY_STATUS_COMPLETED = "COMPLETED";

    @Resource
    private ProjectManualCreationService projectManualCreationService;
    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private IdempotencyRecordService idempotencyRecordService;

    @PostMapping
    @Operation(summary = "手工创建项目（Idempotency-Key 幂等；单事务创建+实例化+可选指派）")
    @PreAuthorize("@ss.hasPermission('pms:project:create')")
    public CommonResult<ProjectCreateRespVO> createProject(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProjectCreateReqVO createReqVO) {
        // 无幂等键：直接创建（不做幂等保护）
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return success(doCreate(createReqVO));
        }
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        String digest = sha256Digest(toJson(createReqVO));
        IdempotencyRecordDO existing = idempotencyRecordService.findByKey(
                currentTenantId(), COMMAND_PROJECT_CREATE, actorId, idempotencyKey);
        if (existing != null) {
            // 同键异摘要 → 409；同键同摘要 → 重放返回原资源
            if (!digest.equals(existing.getRequestDigest())) {
                throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
            }
            return success(fromJson(existing.getResponsePayload()));
        }
        ProjectCreateRespVO created = doCreate(createReqVO);
        saveIdempotencyRecord(idempotencyKey, actorId, digest, toJson(created));
        return success(created);
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
    public CommonResult<Boolean> assignManager(@PathVariable("id") Long id,
                                               @Valid @RequestBody ProjectAssignManagerReqVO assignReqVO) {
        projectManualCreationService.assignServiceManager(id, assignReqVO.getUserId(),
                assignReqVO.getEmployeeNo(), assignReqVO.getMemberName(), assignReqVO.getEffectiveFrom());
        return success(true);
    }

    // ========== 内部方法（幂等支撑） ==========

    private ProjectCreateRespVO doCreate(ProjectCreateReqVO createReqVO) {
        ProjectMasterDO draft = BeanUtils.toBean(createReqVO, ProjectMasterDO.class);
        ProjectMasterDO created = projectManualCreationService.createProject(draft,
                createReqVO.getOrderOfficeCompanyCode(), createReqVO.getOrderOfficeDepartmentCode(),
                createReqVO.getTemplateId(), createReqVO.getServiceManagerUserId());
        ProjectCreateRespVO respVO = BeanUtils.toBean(created, ProjectCreateRespVO.class);
        // 实例化摘要（幂等快照含计数，重放与首响一致）
        ProjectInstantiation instantiation = projectManualCreationService.getInstances(created.getId());
        respVO.setStageCount(instantiation.getStages().size());
        respVO.setTaskCount(instantiation.getTasks().size());
        respVO.setMilestoneCount(instantiation.getMilestones().size());
        respVO.setDeliverableCount(instantiation.getDeliverables().size());
        respVO.setGateCount(instantiation.getGates().size());
        respVO.setServiceManagerAssigned(createReqVO.getServiceManagerUserId() != null);
        return respVO;
    }

    private void saveIdempotencyRecord(String idempotencyKey, Long actorId, String digest, String responsePayload) {
        try {
            IdempotencyRecordDO record = new IdempotencyRecordDO();
            record.setCommand(COMMAND_PROJECT_CREATE);
            record.setActorId(actorId);
            record.setIdempotencyKey(idempotencyKey);
            record.setRequestDigest(digest);
            record.setResponsePayload(responsePayload);
            record.setStatus(IDEMPOTENCY_STATUS_COMPLETED);
            idempotencyRecordService.save(record);
        } catch (Exception ex) {
            // 创建已成功，幂等记录失败仅告警（uk 冲突=并发重放，由重放路径返回原资源）
            log.warn("保存幂等记录失败（command={}, key={}）", COMMAND_PROJECT_CREATE, idempotencyKey, ex);
        }
    }

    private String toJson(Object value) {
        return JsonUtils.toJsonString(value);
    }

    /**
     * 当前租户号：多租户取上下文；单租户（V1 enable=false 无上下文）回退 0，
     * 与各表 tenant_id DDL 默认值一致。
     */
    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId != null ? tenantId : 0L;
    }

    private ProjectCreateRespVO fromJson(String payload) {
        return JsonUtils.parseObject(payload, ProjectCreateRespVO.class);
    }

    private String sha256Digest(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", ex);
        }
    }
}
