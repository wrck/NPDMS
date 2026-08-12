package cn.iocoder.yudao.module.pms.project.controller.admin.phase;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhaseCompleteReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhasePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhaseRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.phase.vo.ProjectPhaseSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.phase.ProjectPhaseDO;
import cn.iocoder.yudao.module.pms.project.service.phase.ProjectPhaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 项目阶段 Controller（FR-PROJ-017 / FR-PROJ-016 / FR-PROJ-019）。
 * <p>
 * 路径前缀 {@code /pms/project-phase}，对应菜单权限 {@code pms:project-phase:*}。
 * 提供阶段 CRUD、模板实例化、阶段顺序校验、完成门禁、超期与临期预警能力。
 */
@Tag(name = "管理后台 - PMS 项目阶段")
@RestController
@RequestMapping("/pms/project-phase")
@Validated
public class ProjectPhaseController {

    @Resource
    private ProjectPhaseService projectPhaseService;

    @PostMapping("/create")
    @Operation(summary = "创建项目阶段")
    @PreAuthorize("@ss.hasPermission('pms:project-phase:update')")
    public CommonResult<Long> createPhase(@Valid @RequestBody ProjectPhaseSaveReqVO createReqVO) {
        return success(projectPhaseService.createPhase(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新项目阶段")
    @PreAuthorize("@ss.hasPermission('pms:project-phase:update')")
    public CommonResult<Boolean> updatePhase(@Valid @RequestBody ProjectPhaseSaveReqVO updateReqVO) {
        projectPhaseService.updatePhase(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目阶段")
    @Parameter(name = "id", description = "阶段编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-phase:update')")
    public CommonResult<Boolean> deletePhase(@RequestParam("id") Long id) {
        projectPhaseService.deletePhase(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除项目阶段")
    @Parameter(name = "ids", description = "阶段编号列表，逗号分隔", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-phase:update')")
    public CommonResult<Boolean> deletePhaseList(@RequestParam("ids") Collection<Long> ids) {
        projectPhaseService.deletePhaseList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询阶段详情")
    @Parameter(name = "id", description = "阶段编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-phase:query')")
    public CommonResult<ProjectPhaseRespVO> getPhase(@RequestParam("id") Long id) {
        ProjectPhaseDO phase = projectPhaseService.getPhase(id);
        return success(BeanUtils.toBean(phase, ProjectPhaseRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询项目阶段")
    @PreAuthorize("@ss.hasPermission('pms:project-phase:query')")
    public CommonResult<PageResult<ProjectPhaseRespVO>> getPhasePage(@Validated ProjectPhasePageReqVO pageReqVO) {
        PageResult<ProjectPhaseDO> pageResult = projectPhaseService.getPhasePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectPhaseRespVO.class));
    }

    @GetMapping("/list-by-project")
    @Operation(summary = "按项目查询全部阶段")
    @Parameter(name = "projectId", description = "项目编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-phase:query')")
    public CommonResult<List<ProjectPhaseRespVO>> getPhaseListByProjectId(
            @RequestParam("projectId") Long projectId) {
        List<ProjectPhaseDO> list = projectPhaseService.getPhaseListByProjectId(projectId);
        return success(BeanUtils.toBean(list, ProjectPhaseRespVO.class));
    }

    @PostMapping("/instantiate-from-template")
    @Operation(summary = "从模板实例化阶段到项目")
    @PreAuthorize("@ss.hasPermission('pms:project-phase:update')")
    public CommonResult<Long> instantiateFromTemplate(@RequestParam("projectId") Long projectId,
                                                      @RequestParam("templateId") Long templateId) {
        return success(projectPhaseService.instantiateFromTemplate(projectId, templateId));
    }

    @GetMapping("/validate-sequence")
    @Operation(summary = "校验阶段顺序（前序阶段须已完成）")
    @Parameter(name = "phaseId", description = "阶段编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-phase:query')")
    public CommonResult<Boolean> validateSequence(@RequestParam("phaseId") Long phaseId) {
        projectPhaseService.validateSequence(phaseId);
        return success(true);
    }

    @PostMapping("/check-gate")
    @Operation(summary = "校验完成门禁")
    @Parameter(name = "phaseId", description = "阶段编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-phase:gate')")
    public CommonResult<Map<String, Object>> checkCompletionGate(@RequestParam("phaseId") Long phaseId) {
        ProjectPhaseService.GateCheckResult result = projectPhaseService.checkCompletionGate(phaseId);
        Map<String, Object> data = new HashMap<>();
        data.put("passed", result.isPassed());
        data.put("unfinishedTaskCount", result.getUnfinishedTaskCount());
        data.put("exitCriteriaDocumented", result.isExitCriteriaDocumented());
        data.put("reason", result.getReason());
        return success(data);
    }

    @PutMapping("/complete")
    @Operation(summary = "完成阶段（含门禁校验）")
    @PreAuthorize("@ss.hasPermission('pms:project-phase:gate')")
    public CommonResult<Boolean> completePhase(@Valid @RequestBody ProjectPhaseCompleteReqVO reqVO) {
        projectPhaseService.completePhase(reqVO.getPhaseId(), reqVO.getGateEvidence(), reqVO.getVersion());
        return success(true);
    }

    @GetMapping("/overdue")
    @Operation(summary = "查询超期阶段列表")
    @PreAuthorize("@ss.hasPermission('pms:project-phase:query')")
    public CommonResult<List<ProjectPhaseRespVO>> getOverduePhases() {
        List<ProjectPhaseDO> list = projectPhaseService.getOverduePhases();
        return success(BeanUtils.toBean(list, ProjectPhaseRespVO.class));
    }

    @GetMapping("/upcoming")
    @Operation(summary = "查询临期截止阶段列表")
    @Parameter(name = "daysWithin", description = "未来天数区间", required = true)
    @PreAuthorize("@ss.hasPermission('pms:project-phase:query')")
    public CommonResult<List<ProjectPhaseRespVO>> getUpcomingPhases(
            @RequestParam(value = "daysWithin", defaultValue = "7") int daysWithin) {
        List<ProjectPhaseDO> list = projectPhaseService.getUpcomingPhases(daysWithin);
        return success(BeanUtils.toBean(list, ProjectPhaseRespVO.class));
    }
}
