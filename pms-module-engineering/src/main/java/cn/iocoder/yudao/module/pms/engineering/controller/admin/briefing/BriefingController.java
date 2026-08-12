package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingGenerateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import cn.iocoder.yudao.module.pms.engineering.service.briefing.BriefingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - PMS 工程交底书 Controller（FR-ENG-006）。
 * <p>
 * 路径前缀 {@code /pms/eng-briefing}，由 Yudao 全局配置追加 {@code /admin-api} 前缀。
 * 对应菜单权限 {@code pms:eng-briefing:*}。
 */
@Tag(name = "管理后台 - PMS 工程交底书")
@RestController
@RequestMapping("/pms/eng-briefing")
@Validated
public class BriefingController {

    @Resource
    private BriefingService briefingService;

    @PostMapping("/create")
    @Operation(summary = "创建工程交底书")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:create')")
    public CommonResult<Long> createBriefing(@Valid @RequestBody BriefingSaveReqVO createReqVO) {
        return success(briefingService.createBriefing(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工程交底书")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:update')")
    public CommonResult<Boolean> updateBriefing(@Valid @RequestBody BriefingSaveReqVO updateReqVO) {
        briefingService.updateBriefing(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工程交底书")
    @Parameter(name = "id", description = "交底书编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:delete')")
    public CommonResult<Boolean> deleteBriefing(@RequestParam("id") Long id) {
        briefingService.deleteBriefing(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "查询工程交底书详情")
    @Parameter(name = "id", description = "交底书编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:query')")
    public CommonResult<BriefingRespVO> getBriefing(@RequestParam("id") Long id) {
        BriefingDO entity = briefingService.getBriefing(id);
        return success(BeanUtils.toBean(entity, BriefingRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询工程交底书")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:query')")
    public CommonResult<PageResult<BriefingRespVO>> getBriefingPage(@Validated BriefingPageReqVO pageReqVO) {
        PageResult<BriefingDO> pageResult = briefingService.getBriefingPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, BriefingRespVO.class));
    }

    @PutMapping("/generate")
    @Operation(summary = "生成工程交底书（0 草稿 → 1 已生成）")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:generate')")
    public CommonResult<Boolean> generateBriefing(@Valid @RequestBody BriefingGenerateReqVO reqVO) {
        briefingService.generateBriefing(reqVO);
        return success(true);
    }

    @PutMapping("/approve")
    @Operation(summary = "审核工程交底书（1 已生成 → 2 已审核 / 0 草稿）")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:audit')")
    public CommonResult<Boolean> approveBriefing(@Valid @RequestBody BriefingApproveReqVO reqVO) {
        briefingService.approveBriefing(reqVO);
        return success(true);
    }

    @PutMapping("/publish")
    @Operation(summary = "发布工程交底书（2 已审核 → 3 已发布）")
    @Parameter(name = "id", description = "交底书编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:publish')")
    public CommonResult<Boolean> publishBriefing(@RequestParam("id") Long id) {
        briefingService.publishBriefing(id);
        return success(true);
    }

    @PutMapping("/terminate")
    @Operation(summary = "作废工程交底书（非 3 已发布 / 非 4 已作废 → 4 已作废）")
    @Parameter(name = "id", description = "交底书编号", required = true)
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:update')")
    public CommonResult<Boolean> terminateBriefing(@RequestParam("id") Long id) {
        briefingService.terminateBriefing(id);
        return success(true);
    }
}
