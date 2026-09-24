package cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo.StageSuggestionRulePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo.StageSuggestionRuleRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectschedule.vo.StageSuggestionRuleSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectschedule.StageSuggestionRuleDO;
import cn.iocoder.yudao.module.pms.project.service.projectschedule.StageSuggestionRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean;

@Tag(name = "管理后台 - PMS 施工计划建议规则")
@RestController
@RequiredArgsConstructor
@RequestMapping("/pms/stage-plan-suggestion-rule")
public class StageSuggestionRuleController {

    private final StageSuggestionRuleService ruleService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('pms:stage-plan-suggestion-rule:query')")
    public CommonResult<PageResult<StageSuggestionRuleRespVO>> getRulePage(@Valid StageSuggestionRulePageReqVO pageReqVO) {
        PageResult<StageSuggestionRuleDO> page = ruleService.getRulePage(pageReqVO);
        return success(new PageResult<>(toBean(page.getList(), StageSuggestionRuleRespVO.class), page.getTotal()));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('pms:stage-plan-suggestion-rule:query')")
    public CommonResult<StageSuggestionRuleRespVO> getRule(@RequestParam("id") Long id) {
        return success(toBean(ruleService.getRule(id), StageSuggestionRuleRespVO.class));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('pms:stage-plan-suggestion-rule:save')")
    public CommonResult<Long> createRule(@Valid @RequestBody StageSuggestionRuleSaveReqVO saveReqVO) {
        return success(ruleService.createRule(saveReqVO));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('pms:stage-plan-suggestion-rule:update')")
    public CommonResult<Boolean> updateRule(@Valid @RequestBody StageSuggestionRuleSaveReqVO saveReqVO) {
        ruleService.updateRule(saveReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('pms:stage-plan-suggestion-rule:delete')")
    public CommonResult<Boolean> deleteRule(@RequestParam("id") Long id) {
        ruleService.deleteRule(id);
        return success(true);
    }
}
