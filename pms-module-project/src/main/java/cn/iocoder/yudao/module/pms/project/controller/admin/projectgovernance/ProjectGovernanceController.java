package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernancePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectGovernanceRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectGovernanceActionDO;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * V1.7项目治理动作历史只读入口。
 * V1.8写动作统一由/pms/projects/{id}/actions/*提供，不再暴露旧CRUD写路由。
 */
@Tag(name = "管理后台 - PMS 项目治理")
@RestController
@RequestMapping("/pms/project-governance")
@Validated
public class ProjectGovernanceController {

    @Resource
    private ProjectGovernanceService projectGovernanceService;

    @GetMapping("/page")
    @Operation(summary = "获得治理动作分页")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:query')")
    public CommonResult<PageResult<ProjectGovernanceRespVO>> getPage(@Validated ProjectGovernancePageReqVO pageReqVO) {
        PageResult<ProjectGovernanceActionDO> pageResult = projectGovernanceService.getGovernanceActionPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ProjectGovernanceRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得治理动作详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:project-governance:query')")
    public CommonResult<ProjectGovernanceRespVO> get(@RequestParam("id") Long id) {
        ProjectGovernanceActionDO entity = projectGovernanceService.getGovernanceAction(id);
        return success(BeanUtils.toBean(entity, ProjectGovernanceRespVO.class));
    }

}
