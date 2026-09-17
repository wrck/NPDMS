package cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo.*;
import cn.iocoder.yudao.module.pms.engineering.service.briefing.entity.BriefingEntityService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** 工程交底独立入口；原 Controller、权限标识和 /pms/eng-briefing 路由均不修改。 */
@RestController
@RequestMapping("/api/v1/pms/engineering-briefings")
@Validated
public class BriefingEntityController {
    @Resource
    private BriefingEntityService briefingEntityService;
    @Resource
    private cn.iocoder.yudao.module.pms.engineering.service.briefing.entity.BriefingEntityImportService importService;

    @PostMapping("/import-legacy")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:create') and @ss.hasPermission('pms:eng-briefing:query')")
    public CommonResult<Long> importLegacy(@RequestParam("id") Long id) {
        return success(importService.importOne(id));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:create')")
    public CommonResult<Long> create(@Valid @RequestBody BriefingEntitySaveReqVO request) {
        return success(briefingEntityService.createBriefing(request));
    }
    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody BriefingEntitySaveReqVO request) {
        briefingEntityService.updateBriefing(request);
        return success(true);
    }
    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        briefingEntityService.deleteBriefing(id);
        return success(true);
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:query')")
    public CommonResult<BriefingEntityRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(briefingEntityService.getBriefing(id), BriefingEntityRespVO.class));
    }
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:query')")
    public CommonResult<PageResult<BriefingEntityRespVO>> page(@Validated BriefingEntityPageReqVO request) {
        return success(BeanUtils.toBean(briefingEntityService.getBriefingPage(request), BriefingEntityRespVO.class));
    }
    @PutMapping("/generate")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:generate')")
    public CommonResult<Boolean> generate(@Valid @RequestBody BriefingEntityGenerateReqVO request) {
        briefingEntityService.generateBriefing(request);
        return success(true);
    }
    @PutMapping("/approve")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody BriefingEntityApproveReqVO request) {
        briefingEntityService.approveBriefing(request);
        return success(true);
    }
    @PutMapping("/publish")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:publish')")
    public CommonResult<Boolean> publish(@RequestParam("id") Long id) {
        briefingEntityService.publishBriefing(id);
        return success(true);
    }
    @PutMapping("/terminate")
    @PreAuthorize("@ss.hasPermission('pms:eng-briefing:update')")
    public CommonResult<Boolean> terminate(@RequestParam("id") Long id) {
        briefingEntityService.terminateBriefing(id);
        return success(true);
    }
}
