package cn.iocoder.yudao.module.pms.service.controller.admin.srvrule;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRulePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRuleRespVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvrule.vo.SrvRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvrule.SrvRuleDO;
import cn.iocoder.yudao.module.pms.service.service.srvrule.SrvRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 巡检规则")
@RestController
@RequestMapping("/pms/srv-rule")
@Validated
public class SrvRuleController {

    @Resource
    private SrvRuleService srvRuleService;

    @PostMapping("/create")
    @Operation(summary = "创建巡检规则")
    @PreAuthorize("@ss.hasPermission('pms:srv-rule:create')")
    public CommonResult<Long> createSrvRule(@Valid @RequestBody SrvRuleSaveReqVO createReqVO) {
        return success(srvRuleService.createSrvRule(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新巡检规则")
    @PreAuthorize("@ss.hasPermission('pms:srv-rule:update')")
    public CommonResult<Boolean> updateSrvRule(@Valid @RequestBody SrvRuleSaveReqVO updateReqVO) {
        srvRuleService.updateSrvRule(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除巡检规则")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-rule:delete')")
    public CommonResult<Boolean> deleteSrvRule(@RequestParam("id") Long id) {
        srvRuleService.deleteSrvRule(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得巡检规则分页")
    @PreAuthorize("@ss.hasPermission('pms:srv-rule:query')")
    public CommonResult<PageResult<SrvRuleRespVO>> getSrvRulePage(@Validated SrvRulePageReqVO pageReqVO) {
        PageResult<SrvRuleDO> pageResult = srvRuleService.getSrvRulePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, SrvRuleRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得巡检规则")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-rule:query')")
    public CommonResult<SrvRuleRespVO> getSrvRule(@RequestParam("id") Long id) {
        SrvRuleDO rule = srvRuleService.getSrvRule(id);
        return success(BeanUtils.toBean(rule, SrvRuleRespVO.class));
    }

    @PutMapping("/publish")
    @Operation(summary = "发布巡检规则")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-rule:update')")
    public CommonResult<Boolean> publishSrvRule(@RequestParam("id") Long id) {
        srvRuleService.publishSrvRule(id);
        return success(true);
    }

    @PutMapping("/disable")
    @Operation(summary = "停用巡检规则")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('pms:srv-rule:update')")
    public CommonResult<Boolean> disableSrvRule(@RequestParam("id") Long id) {
        srvRuleService.disableSrvRule(id);
        return success(true);
    }

}
