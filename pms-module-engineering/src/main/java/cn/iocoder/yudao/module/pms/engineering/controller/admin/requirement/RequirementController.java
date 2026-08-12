package cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementDO;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 需求分析 Controller（FR-ENG-004）。
 * <p>
 * 路径前缀 {@code /pms/eng-requirement}。
 */
@Tag(name = "管理后台 - 需求分析")
@RestController
@RequestMapping("/pms/eng-requirement")
@Validated
public class RequirementController {

    @Resource
    private RequirementService requirementService;

    @PostMapping("/create")
    @Operation(summary = "创建需求分析")
    @PreAuthorize("@ss.hasPermission('pms:eng-requirement:create')")
    public CommonResult<Long> createRequirement(@Valid @RequestBody RequirementSaveReqVO createReqVO) {
        return success(requirementService.createRequirement(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新需求分析")
    @PreAuthorize("@ss.hasPermission('pms:eng-requirement:update')")
    public CommonResult<Boolean> updateRequirement(@Valid @RequestBody RequirementSaveReqVO updateReqVO) {
        requirementService.updateRequirement(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除需求分析")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-requirement:delete')")
    public CommonResult<Boolean> deleteRequirement(@RequestParam("id") Long id) {
        requirementService.deleteRequirement(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询需求分析详情")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-requirement:query')")
    public CommonResult<RequirementRespVO> getRequirement(@RequestParam("id") Long id) {
        RequirementDO requirement = requirementService.getRequirement(id);
        return success(BeanUtils.toBean(requirement, RequirementRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询需求分析")
    @PreAuthorize("@ss.hasPermission('pms:eng-requirement:query')")
    public CommonResult<PageResult<RequirementRespVO>> getRequirementPage(@Validated RequirementPageReqVO pageReqVO) {
        PageResult<RequirementDO> pageResult = requirementService.getRequirementPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, RequirementRespVO.class));
    }

    @PutMapping("/submit")
    @Operation(summary = "提交需求")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-requirement:update')")
    public CommonResult<Boolean> submitRequirement(@RequestParam("id") Long id) {
        requirementService.submitRequirement(id);
        return success(true);
    }

    @PutMapping("/mark-effective")
    @Operation(summary = "标记需求生效")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-requirement:update')")
    public CommonResult<Boolean> markEffective(@RequestParam("id") Long id) {
        requirementService.markEffective(id);
        return success(true);
    }

    @PutMapping("/archive")
    @Operation(summary = "归档需求")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-requirement:update')")
    public CommonResult<Boolean> archiveRequirement(@RequestParam("id") Long id) {
        requirementService.archiveRequirement(id);
        return success(true);
    }
}
